package pravda.vm.asm

import java.nio.ByteBuffer

import com.google.protobuf.ByteString
import fastparse.all
import fastparse.core.Parsed
import fastparse.parsers.Combinators.Rule
import pravda.vm.{Data, Opcodes, Meta => Metadata}

import scala.annotation.switch
import scala.collection.mutable

/**
  * Low-level utility to work with Pravda VM bytecode.
  * [[PravdaAssembler]] operates with sequences of [[Operation]].
  * It can generate bytecode representation and
  * text representation of operations. Also it can
  * disassemble bytecode to sequence of [[Operation]].
  */
object PravdaAssembler {

  import Data._
  import Operation._

  /**
    * Parses text and assemble to Pravda VM bytecode.
    * @param saveLabels Add META opcodes with infomation about labels.
    *                   It can be used by disassembler.
    * @return Error | Bytecode
    */
  def assemble(text: String, saveLabels: Boolean): Either[String, ByteString] = {
    parser.parse(text) match {
      case Parsed.Success(operations, _) => Right(assemble(operations, saveLabels))
      case failure @ Parsed.Failure(_, _, _) =>
        Left(failure.extra.input.repr.errorMessage(failure.extra.input, failure.extra.traced.expected, failure.index))
    }
  }

  /**
    * Generates Pavda VM compatible bytecode from sequence of operations.
    * You can obtain sequence of operations using [[parser]].
    * @param saveLabels Add META opcodes with infomation about labels.
    *                   It can be used by disassembler.
    */
  def assemble(operations: Seq[Operation], saveLabels: Boolean): ByteString = {
    val labels = mutable.Map.empty[String, Int] // label -> offset
    val gotos = mutable.Map.empty[Int, String] // offset -> label
    val bytecode = ByteBuffer.allocate(1024 * 1024)

    def putOp(opcode: Int): Unit =
      bytecode.put(opcode.toByte)

    /** Go to `name` using `opcode` */
    def goto(name: String, opcode: Int): Unit = {
      if (saveLabels) {
        putOp(Opcodes.META)
        Metadata.LabelUse(name).writeToByteBuffer(bytecode)
      }
      putOp(Opcodes.PUSHX)
      // Save goto offset to set in a future
      gotos.put(bytecode.position, name)
      // Just a placeholer. Ref is constant sized
      Primitive.Ref.Void.writeToByteBuffer(bytecode)
      putOp(opcode)
    }

    for (operation <- operations if operation != Nop) operation match {
      case StructGet(Some(key)) =>
        putOp(Opcodes.STRUCT_GET_STATIC)
        key.writeToByteBuffer(bytecode)
      case StructGet(None) =>
        putOp(Opcodes.STRUCT_GET)
      case StructMut(None) =>
        putOp(Opcodes.STRUCT_MUT)
      case StructMut(Some(key)) =>
        putOp(Opcodes.STRUCT_MUT_STATIC)
        key.writeToByteBuffer(bytecode)
      case Push(data) =>
        putOp(Opcodes.PUSHX)
        data.writeToByteBuffer(bytecode)
      case New(data) =>
        putOp(Opcodes.NEW)
        data.writeToByteBuffer(bytecode)
      // Control
      case Label(name) =>
        if (saveLabels) {
          putOp(Opcodes.META)
          Metadata.LabelDef(name).writeToByteBuffer(bytecode)
        }
        labels.put(name, bytecode.position)
      case Meta(data) =>
        putOp(Opcodes.META)
        data.writeToByteBuffer(bytecode)
      case Jump(Some(name))  => goto(name, Opcodes.JUMP)
      case JumpI(Some(name)) => goto(name, Opcodes.JUMPI)
      case Call(Some(name))  => goto(name, Opcodes.CALL)
      case Jump(None)        => putOp(Opcodes.JUMP)
      case JumpI(None)       => putOp(Opcodes.JUMPI)
      case Call(None)        => putOp(Opcodes.CALL)
      case Orphan(opcode, _) => putOp(opcode)
      // Simple operations
      case Nop        => ()
      case _: Comment => ()
    }

    // Size will be not changed anymore
    bytecode.flip()

    for ((offset, label) <- gotos) {
      val data = Primitive.Ref(labels(label))
      // Replace placeholder with real offset
      bytecode.position(offset)
      data.writeToByteBuffer(bytecode)
    }

    bytecode.rewind()
    ByteString.copyFrom(bytecode)
  }

  /**
    * Disassembles bytecode to sequence of operations.
    */
  def disassemble(bytecode: ByteString): Seq[Operation] = {
    val buffer = bytecode.asReadOnlyByteBuffer()
    val operations = mutable.Buffer.empty[Operation]
    var label = Option.empty[String]
    while (buffer.hasRemaining) {
      val opcode = buffer.get & 0xff
      operations += Operation.operationByCode.getOrElse(
        key = opcode,
        default = (opcode: @switch) match {
          case Opcodes.META =>
            Metadata.readFromByteBuffer(buffer) match {
              case Metadata.LabelDef(name) => Operation.Label(name)
              case Metadata.LabelUse(name) =>
                label = Some(name)
                Operation.Nop
              case metadata => Operation.Meta(metadata)
            }
          case Opcodes.STRUCT_GET_STATIC =>
            val offset = buffer.position
            Data.readFromByteBuffer(buffer) match {
              case key: Primitive => StructGet(Some(key))
              case data           => throw TypeUnexpectedException(data.getClass, offset)
            }
          case Opcodes.STRUCT_MUT_STATIC =>
            val offset = buffer.position
            Data.readFromByteBuffer(buffer) match {
              case key: Primitive => StructMut(Some(key))
              case data           => throw TypeUnexpectedException(data.getClass, offset)
            }
          case Opcodes.PUSHX if label.nonEmpty =>
            Data.readFromByteBuffer(buffer)
            Operation.Nop
          case Opcodes.JUMP if label.nonEmpty =>
            val op = Operation.Jump(label)
            label = None
            op
          case Opcodes.JUMPI if label.nonEmpty =>
            val op = Operation.JumpI(label)
            label = None
            op
          case Opcodes.CALL if label.nonEmpty =>
            val op = Operation.Call(label)
            label = None
            op
          case Opcodes.NEW   => Operation.New(Data.readFromByteBuffer(buffer))
          case Opcodes.PUSHX => Operation.Push(Data.readFromByteBuffer(buffer))
          case Opcodes.JUMP  => Operation.Jump(None)
          case Opcodes.JUMPI => Operation.JumpI(None)
          case Opcodes.CALL  => Operation.Call(None)
          case Opcodes.STRUCT_GET => Operation.StructGet(None)
          case Opcodes.STRUCT_MUT => Operation.StructMut(None)
        }
      )
    }

    operations.filter(_ != Nop)
  }

  /**
    * Prints one operation to string.
    */
  def render(operation: Operation): String = operation match {
    case Jump(Some(label))  => s"${operation.mnemonic} @$label"
    case JumpI(Some(label)) => s"${operation.mnemonic} @$label"
    case Call(Some(label))  => s"${operation.mnemonic} @$label"
    case Comment(value)     => s"/*$value*/"
    case Push(data)         => s"${operation.mnemonic} ${data.mkString(pretty = true)}"
    case New(data)          => s"${operation.mnemonic} ${data.mkString(pretty = true)}"
    case Label(name)        => s"@$name:"
    case StructGet(Some(k)) => s"${operation.mnemonic} ${k.mkString(pretty = true)}"
    case StructMut(Some(k)) => s"${operation.mnemonic} ${k.mkString(pretty = true)}"
    case _                  => operation.mnemonic
  }

  /**
    * Prints sequence of operation to string.
    * Received text is valid assembler text
    * which may be parsed by [[parser]].
    */
  def render(operations: Seq[Operation]): String =
    operations.map(render).mkString("\n")

  /**
    * Use this to parse assembler text
    * to sequence of operations.
    */
  val parser: all.Parser[Seq[Operation]] = {

    import fastparse.all._

    import Data.parser.{primitive => dataPrimitive, all => dataAll}
    val digit = P(CharIn('0' to '9'))
    val alpha = P(CharIn('a' to 'z', 'A' to 'Z'))
    val alphadig = P(alpha | digit)
    val ident = P("@" ~ (alpha.rep(1) ~ alphadig.rep(1).?).!)
    val whitespace = P(CharIn(" \t\r\n").rep)
    val delim = P(CharIn(" \t\r\n").rep(min = 1))

    val label = P(ident ~ ":").map(n => Operation.Label(n))
    val push = P(IgnoreCase("push") ~ delim ~ dataPrimitive).map(Operation.Push)
    val `new` = P(IgnoreCase("new") ~ delim ~ dataAll).map(Operation.New)
    val jump = P(IgnoreCase("jump") ~ (delim ~ ident).?).map(Operation.Jump)
    val jumpi = P(IgnoreCase("jumpi") ~ (delim ~ ident).?).map(Operation.JumpI)
    val call = P(IgnoreCase("call") ~ (delim ~ ident).?).map(Operation.Call)
    val struct_get = P(IgnoreCase("struct_get") ~ (delim ~ dataPrimitive).?).map(Operation.StructGet)
    val struct_mut = P(IgnoreCase("struct_mut") ~ (delim ~ dataPrimitive).?).map(Operation.StructMut)
    val comment = P("/*" ~ (!"*/" ~ AnyChar).rep.! ~ "*/").map(Operation.Comment)

    val operation = Operation.Orphans
      .map(op => Rule(op.mnemonic, () => IgnoreCase(op.mnemonic)).map(_ => op))
      .++(Seq(jumpi, jump, call, push, `new`, struct_get, struct_mut, label, comment))
      .reduce(_ | _)

    P(Start ~ whitespace ~ operation.rep(sep = delim) ~ whitespace ~ End)
  }
}
