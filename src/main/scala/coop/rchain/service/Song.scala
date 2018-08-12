package coop.rchain.service

import coop.rchain.model._
import org.http4s.Uri

/** service layer.
  * Des provides Repo services to api layer
  */
object SongService {
  def mySongs(userId: String, cursor: Cursor): List[SongMetadata] = {
   List(
     SongMetadata(
       song=Song(isrc="rc-B123", uri = "https://someurl-1", duration_ms=1000L,language="EN"),
       artists = List(Artist(id="artist-1", name="famous-artist-1")),
       artwork = List(Artwork(id="rc-artwork-1", uri="https://artwork-url-1"))
     ),
     SongMetadata(
       song=Song(isrc="rc-B123", uri = "https://someurl-1", duration_ms=1000L,language="EN"),
       artists = List(Artist(id="artist-2", name="famous-artist-2")),
       artwork = List(Artwork(id="rc-artwork-2", uri="https://artwork-url-2"))
     )
   )
  }

}
