package pl.lambada.songsync.util

class NoTrackFoundException : Exception()
class InternalErrorException(msg: String) : Exception(msg)
class EmptyQueryException : Exception()

/** The provider is sure the song is an instrumental: there are no lyrics to look for elsewhere. */
class InstrumentalException : Exception()

/** The provider can't be used right now (e.g. not available in the user's region). */
class ProviderUnavailableException(msg: String) : Exception(msg)