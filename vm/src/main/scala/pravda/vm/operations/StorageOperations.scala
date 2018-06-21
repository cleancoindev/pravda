package pravda.vm.operations

import pravda.vm.VmError.OperationDenied
import pravda.vm.WattCounter._
import pravda.vm.Data.Primitive
import pravda.vm._

/**
  * Pravda VM storage pravda.vm.Opcodes implementation.
  * @see [[pravda.vm.Opcodes]]
  * @param memory Access to VM memory
  * @param maybeStorage Access to program's storage
  */
final class StorageOperations(memory: Memory, maybeStorage: Option[Storage], wattCounter: WattCounter) {

  /**
    * Pops first item from stack, interprets it as key and
    * checks existence of record correspond to the key in a storage of the program.
    * @see [[pravda.vm.Opcodes.SEXIST]]
    */
  def exists(): Unit = ifStorage { storage =>
    val key = memory.pop()
    val defined = storage.get(key).isDefined
    val data = Primitive.Bool(defined)
    wattCounter.cpuUsage(CpuStorageUse)
    wattCounter.memoryUsage(data.volume.toLong)
    memory.push(data)
  }

  /**
    * Pops first item from stack, interprets it as key and
    * removes corresponding record from a storage of the program.
    * @see [[pravda.vm.Opcodes.SDROP]]
    */
  def drop(): Unit = ifStorage { storage =>
    // TODO fomkin: consider to push removed value to the stack
    val key = memory.pop()
    val keyBytes = key.toByteString
    val maybePrevious = storage.delete(Data.MarshalledData(keyBytes))

    wattCounter.cpuUsage(CpuStorageUse)
    maybeReleaseStorage(keyBytes.size(), maybePrevious)
  }

  /**
    * Pops first item from stack, interprets it as key,
    * retrieves corresponding record from a storage of the program and
    * pushes the record to the stack. Otherwise throws an exception.
    * @see [[pravda.vm.Opcodes.SGET]]
    */
  def get(): Unit = ifStorage { storage =>
    val data = storage.get(memory.pop()).getOrElse(Data.Primitive.Null)
    wattCounter.cpuUsage(CpuStorageUse)
    wattCounter.memoryUsage(data.volume.toLong)
    memory.push(Data.Primitive.Null)
  }

  /**
    * Pops first item from stack, interprets it as key.
    * Pops second item from stack, interprets it as value.
    * Puts (key -> value) record to program's storage.
    * @see [[pravda.vm.Opcodes.SPUT]]
    */
  def put(): Unit = ifStorage { storage =>
    val value = memory.pop()
    val key = memory.pop()
    val keyBytes = key.toByteString
    val valueBytes = value.toByteString
    val bytesTotal = valueBytes.size().toLong + keyBytes.size().toLong
    val maybePrevious = storage.put(Data.MarshalledData(keyBytes), Data.MarshalledData(valueBytes))

    wattCounter.cpuUsage(CpuStorageUse)
    wattCounter.storageUsage(occupiedBytes = bytesTotal)
    maybeReleaseStorage(keyBytes.size(), maybePrevious)
  }

  private def ifStorage(f: Storage => Unit): Unit = maybeStorage match {
    case None          => throw VmErrorException(OperationDenied)
    case Some(storage) => f(storage)
  }

  private def maybeReleaseStorage(keySize: Int, maybePrevious: Option[Data]): Unit = {
    maybePrevious foreach { previous =>
      val releasedBytesTotal = keySize.toLong + previous.volume.toLong
      wattCounter.storageUsage(releasedBytes = releasedBytesTotal)
    }
  }
}
