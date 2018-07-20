/*
 * Copyright (C) 2018  Expload.com
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package pravda.dotnet.translation.opcode

import pravda.dotnet.parsers.CIL
import pravda.dotnet.parsers.CIL._
import pravda.dotnet.translation.data._
import pravda.vm.asm
import pravda.vm.{Data, Opcodes}

case object SimpleTranslations extends OneToManyTranslatorOnlyAsm {

  override def asmOpsOne(op: CIL.Op,
                         stackOffsetO: Option[Int],
                         ctx: MethodTranslationCtx): Either[TranslationError, List[asm.Operation]] = {

    val translateF: PartialFunction[CIL.Op, List[asm.Operation]] = {
      case LdcI40     => List(pushInt(0))
      case LdcI41     => List(pushInt(1))
      case LdcI42     => List(pushInt(2))
      case LdcI43     => List(pushInt(3))
      case LdcI44     => List(pushInt(4))
      case LdcI45     => List(pushInt(5))
      case LdcI46     => List(pushInt(6))
      case LdcI47     => List(pushInt(7))
      case LdcI48     => List(pushInt(8))
      case LdcI4M1    => List(pushInt(-1))
      case LdcI4(num) => List(pushInt(num))
      case LdcI4S(v)  => List(pushInt(v.toInt))
      case LdcR4(f)   => List(pushFloat(f.toDouble))
      case LdcR8(d)   => List(pushFloat(d))
      case LdStr(s)   => List(asm.Operation.Push(Data.Primitive.Utf8(s)))

      case Add => List(asm.Operation(Opcodes.ADD))
      case Mul => List(asm.Operation(Opcodes.MUL))
      case Div => List(asm.Operation(Opcodes.SWAP), asm.Operation(Opcodes.DIV))
      case Rem => List(asm.Operation(Opcodes.SWAP), asm.Operation(Opcodes.MOD))
      case Sub => List(pushInt(-1), asm.Operation(Opcodes.MUL), asm.Operation(Opcodes.ADD))

      case Clt =>
        asm.Operation(Opcodes.SWAP) :: asm.Operation(Opcodes.LT) :: cast(Data.Type.Int32)
      case Cgt =>
        asm.Operation(Opcodes.SWAP) :: asm.Operation(Opcodes.GT) :: cast(Data.Type.Int32)
      case Ceq =>
        asm.Operation(Opcodes.EQ) :: cast(Data.Type.Int32)
      case Not =>
        cast(Data.Type.Boolean) ++ (asm.Operation(Opcodes.NOT) :: cast(Data.Type.Int32))

      case Dup => List(asm.Operation.Orphan(Opcodes.DUP))

      case Nop => List()
      case Ret => List()
    }

    translateF.lift(op).toRight(UnknownOpcode)
  }
}