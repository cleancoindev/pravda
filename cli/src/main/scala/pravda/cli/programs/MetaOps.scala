package pravda.cli.programs

import cats._
import cats.implicits._
import com.google.protobuf.ByteString
import pravda.node.client.{CompilersLanguage, IpfsLanguage, MetadataLanguage}
import pravda.vm.Meta
import pravda.vm.asm.Operation

import scala.collection.mutable
import scala.language.higherKinds

object MetaOps {

  private def mergeMetas(metas: Seq[Map[Int, Seq[Meta]]]): Map[Int, Seq[Meta]] = {
    val m = mutable.Map[Int, mutable.Buffer[Meta]]()
    metas.foreach[Unit](_.foreach[Unit] {
      case (k, v) =>
        m.get(k) match {
          case Some(buff) => buff ++= v
          case None       => m += k -> mutable.Buffer(v: _*)
        }
    })
    m.toMap
  }

  def loadMetaFromSource[F[_]: Monad](source: ByteString)(compilers: CompilersLanguage[F]): F[Map[Int, Seq[Meta]]] =
    for {
      ops <- compilers.disasmToOps(source)
    } yield {
      val metas = mutable.Map[Int, mutable.Buffer[Meta]]()
      ops.foreach {
        case (i, Operation.Meta(m)) =>
          if (metas.contains(i)) {
            metas(i) += m
          } else {
            metas(i) = mutable.Buffer[Meta](m)
          }
        case _ =>
      }

      metas.toMap
    }

  def loadAllMeta[F[_]: Monad](source: ByteString, ipfsNode: String)(
      compilers: CompilersLanguage[F],
      ipfs: IpfsLanguage[F],
      metadata: MetadataLanguage[F]): F[Map[Int, Seq[Meta]]] =
    for {
      sourceMeta <- loadMetaFromSource(source)(compilers)
      includes <- metadata.readPrefixIncludes(source)
      includesMeta <- loadIncludes(includes, ipfsNode)(ipfs)
    } yield mergeMetas(Seq(sourceMeta, includesMeta))

  def loadIncludes[F[_]: Monad](includes: Seq[Meta.MetaInclude], ipfsNode: String)(
      ipfs: IpfsLanguage[F]): F[Map[Int, Seq[Meta]]] = {
    for {
      externalFiles <- includes
        .map {
          case Meta.IpfsFile(hash) => ipfs.loadFromIpfs(ipfsNode, hash)
        }
        .toList
        .sequence
        .map(_.flatten)
      externalMetas = externalFiles.map(bs => Meta.externalReadFromByteBuffer(bs.asReadOnlyByteBuffer()))
    } yield mergeMetas(externalMetas)
  }
}
