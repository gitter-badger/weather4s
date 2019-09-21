package io.sherpair.w4s.geo.engine

import io.sherpair.w4s.domain.{epochAsLong, unit, Meta}
import io.sherpair.w4s.domain.Meta.indexName
import io.sherpair.w4s.geo.{BaseSpec, ImplicitsSyncIO}

class EngineOpsMetaSpec extends BaseSpec {

  "createIndexIfNotExists" when {
    "the \"meta\" index does not exist yet in the engine" should {
      "successfully initialise the Meta object" in new ImplicitsSyncIO {
        val metas = for {
          metaFromInitialise <- engineOps.engineOpsMeta.createIndexIfNotExists
          // Also checking the Meta object is successfully saved in the engine
          metaFromEngine <- engineOps.engineOpsMeta.loadMeta
        }
        yield (metaFromInitialise, metaFromEngine)

        val (metaFromInitialise, metaFromEngine) = metas.unsafeRunSync
        metaFromInitialise shouldBe Meta(epochAsLong)
        metaFromEngine shouldBe Some(Meta(epochAsLong))
      }
    }

    "the \"meta\" index does already exist in the engine" should {
      "successfully load the Meta object from the engine" in new ImplicitsSyncIO {
        val metas = for {
          metaFromInitialise <- engineOps.engineOpsMeta.initialiseMeta
          metaFromEngine <- engineOps.engineOpsMeta.firstMetaLoad
        }
        yield (metaFromInitialise, metaFromEngine)

        val (metaFromInitialise, metaFromEngine) = metas.unsafeRunSync
        metaFromInitialise shouldBe Meta(epochAsLong)
        metaFromEngine shouldBe Meta(epochAsLong)
      }
    }

    "the \"meta\" index does already exist in the engine" should {
      "throw an exception if it does not contain the expected Meta object" in new ImplicitsSyncIO {
        val exc = intercept[IllegalArgumentException] {
          (for {
            _ <- engine.createIndex(indexName)
            _ <- engine.indexExists(indexName)
            _ <- engineOps.engineOpsMeta.createIndexIfNotExists
          } yield unit).unsafeRunSync
        }

        exc.getMessage shouldBe s"requirement failed: ${Meta.requirement}"
      }
    }
  }
}