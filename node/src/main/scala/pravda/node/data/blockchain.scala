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

package pravda.node.data

import cats.Show
import com.google.protobuf.ByteString
import pravda.common.domain._
import supertagged.TaggedType

object blockchain {

  final case class SignatureData(address: Address,
                                 data: TransactionData,
                                 wattLimit: Long,
                                 wattPrice: NativeCoin,
                                 nonce: Int,
                                 wattPayer: Option[Address])

  sealed trait Transaction {
    def from: Address
    def program: TransactionData
    def wattLimit: Long
    def wattPrice: NativeCoin
    def wattPayer: Option[Address]
    def nonce: Int

    def forSignature: SignatureData =
      SignatureData(from, program, wattLimit, wattPrice, nonce, wattPayer)
  }

  object Transaction {

    final case class UnsignedTransaction(from: Address,
                                         program: TransactionData,
                                         wattLimit: Long,
                                         wattPrice: NativeCoin,
                                         wattPayer: Option[Address],
                                         nonce: Int)
        extends Transaction

    final case class SignedTransaction(from: Address,
                                       program: TransactionData,
                                       signature: ByteString,
                                       wattLimit: Long,
                                       wattPrice: NativeCoin,
                                       wattPayer: Option[Address],
                                       wattPayerSignature: Option[ByteString],
                                       nonce: Int)
        extends Transaction

    import pravda.common.bytes

    object SignedTransaction {
      implicit val showInstance: Show[SignedTransaction] = { t =>
        val from = bytes.byteString2hex(t.from)
        val program = bytes.byteString2hex(t.program)
        val signature = bytes.byteString2hex(t.signature)
        s"transaction.signed[from=$from,program=$program,signature=$signature,nonce=${t.nonce},wattLmit=${t.wattLimit},wattPrice=${t.wattPrice}]"
      }
    }

    final case class AuthorizedTransaction(from: Address,
                                           program: TransactionData,
                                           signature: ByteString,
                                           wattLimit: Long,
                                           wattPrice: NativeCoin,
                                           wattPayer: Option[Address],
                                           wattPayerSignature: Option[ByteString],
                                           nonce: Int)
        extends Transaction
  }

  object TransactionData extends TaggedType[ByteString]
  type TransactionData = TransactionData.Type

}
