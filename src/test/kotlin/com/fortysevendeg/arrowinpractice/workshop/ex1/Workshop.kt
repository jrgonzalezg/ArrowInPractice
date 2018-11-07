package com.fortysevendeg.arrowinpractice.workshop.ex1

import arrow.core.Option
import arrow.core.toOption
import arrow.effects.IO
import arrow.effects.fix
import arrow.effects.instances.io.applicative.applicative
import arrow.effects.instances.io.applicativeError.handleErrorWith
import arrow.effects.instances.io.monad.monad
import arrow.instances.option.applicative.map
import arrow.typeclasses.binding
import com.fortysevendeg.arrowinpractice.database.CastlesDatabase
import com.fortysevendeg.arrowinpractice.database.CharactersDatabase
import com.fortysevendeg.arrowinpractice.database.HousesDatabase
import com.fortysevendeg.arrowinpractice.error.InvalidIdException
import com.fortysevendeg.arrowinpractice.error.NotFoundException
import com.fortysevendeg.arrowinpractice.model.Character
import com.fortysevendeg.arrowinpractice.model.HouseLocation
import com.fortysevendeg.arrowinpractice.workshop.utils.getIO
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.auth.authenticate
import io.ktor.routing.Routing

fun paramOf(name: String, call: ApplicationCall): Option<String> =
        call.parameters[name].toOption()

fun IO.Companion.idOrNotFound(maybeId: Option<String>): IO<String> =
        maybeId.fold(
                { raiseError(NotFoundException()) },
                { just(it) }
        )

fun IO.Companion.stringIdToLong(id: String): IO<Long> =
        IO { id.toLong() }.handleErrorWith { raiseError(InvalidIdException()) }

fun IO.Companion.fetchCharacterById(charactersDB: CharactersDatabase, characterId: Long): IO<Character> =
        charactersDB[characterId].fold(
                { raiseError(NotFoundException()) },
                { just(it) }
        )

fun IO.Companion.handleDBExceptions(charactersFetch: IO<Character>): IO<Character> =
        charactersFetch.handleErrorWith { throwable ->
            raiseError(throwable.takeIf { it is NotFoundException } ?: InvalidIdException())
        }

fun IO.Companion.handleDBExceptions2(charactersFetch: IO<Character>): IO<Character> =
        charactersFetch.handleErrorWith { throwable ->
            raiseError(
                    when (throwable) {
                        is NotFoundException -> throwable
                        else -> InvalidIdException()
                    }
            )
        }

private fun <T> T?.toIO(): IO<T> = toOption().fold({ IO.raiseError(NotFoundException()) }, { IO.just(it) })

fun IO.Companion.houseAndLocationEndpoint(
        housesDB: HousesDatabase,
        castlesDB: CastlesDatabase,
        houseId: Long,
        castleId: Long): IO<HouseLocation> =
        applicative().map(
                IO.defer { housesDB[houseId].toIO() },
                IO.defer { castlesDB[castleId].toIO() }
        ) { (house, castle) -> HouseLocation(house, castle) }.fix()

fun IO.Companion.houseAndLocationEndpoint2(
        housesDB: HousesDatabase,
        castlesDB: CastlesDatabase,
        houseId: Long,
        castleId: Long): IO<HouseLocation> =
        applicative().map(
                housesDB[houseId].toIO(),
                castlesDB[castleId].toIO()
        ) { (house, castle) -> HouseLocation(house, castle) }.fix()

fun IO.Companion.houseAndLocationEndpointFromSolutions(
        housesDB: HousesDatabase,
        castlesDB: CastlesDatabase,
        houseId: Long,
        castleId: Long): IO<HouseLocation> =
        map(housesDB[houseId].toOption(), castlesDB[castleId].toOption()) { (house, castle) ->
            HouseLocation(house, castle)
        }.fold({ raiseError(NotFoundException()) }) {
            just(it)
        }

/**
 * GET: Provides the character details for a given character Id.
 *
 * Authentication: Basic (user:password encoded in Base64).
 */
fun Routing.characterDetailsEndpoint(charactersDB: CharactersDatabase) {
    authenticate {
        getIO("/characters/{characterId}") { pipelineContext ->
            monad().binding {
                val maybeId: Option<String> = paramOf("characterId", pipelineContext.call)
                val characterIdString: String = idOrNotFound(maybeId).bind()
                val characterId = stringIdToLong(characterIdString).bind()
                val character = handleDBExceptions(fetchCharacterById(charactersDB, characterId)).bind()
                character
            }
        }
    }
}

/**
 * GET: Provides Castle details for both Castles where Jamie Lannister has a seat (Casterly Rock & Red Keep).
 *
 * Both database queries are completely independent and can fail, but we want to combine results in the end into a
 * single list of Castles.
 *
 * Authentication: Basic (user:password encoded in Base64).
 */
fun Routing.houseAndLocationEndpoint(
        housesDB: HousesDatabase,
        castlesDB: CastlesDatabase
) =
        authenticate {
            getIO("/characters/house/{houseId}/castle/{castleId}") { pipelineContext ->
                monad().binding {
                    val maybeHouseId = paramOf("houseId", pipelineContext.call)
                    val maybeCastleId = paramOf("castleId", pipelineContext.call)
                    val houseId = idOrNotFound(maybeHouseId).bind()
                    val castleId = idOrNotFound(maybeCastleId).bind()
                    val hId = stringIdToLong(houseId).bind()
                    val cId = stringIdToLong(castleId).bind()
                    houseAndLocationEndpoint(housesDB, castlesDB, hId, cId)
                }
            }
        }







