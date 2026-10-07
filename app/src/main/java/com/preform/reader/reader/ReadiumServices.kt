package com.preform.reader.reader

import android.content.Context
import android.net.Uri
import org.readium.adapter.pdfium.document.PdfiumDocumentFactory
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.toAbsoluteUrl
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

/** Readium's opening machinery, built once per process. */
class ReadiumServices(context: Context) {

    private val app = context.applicationContext
    private val httpClient = DefaultHttpClient()
    private val assetRetriever = AssetRetriever(app.contentResolver, httpClient)
    private val publicationOpener = PublicationOpener(
        publicationParser = DefaultPublicationParser(
            context = app,
            httpClient = httpClient,
            assetRetriever = assetRetriever,
            pdfFactory = PdfiumDocumentFactory(app),
        ),
    )

    /**
     * Opens a book from a `content://` (library folder) or `file://` (import) URI.
     * The caller owns the returned publication and must close it.
     */
    suspend fun open(uri: String): Result<Publication> {
        val url = Uri.parse(uri).toAbsoluteUrl()
            ?: return Result.failure(IllegalArgumentException("Not an openable URI: $uri"))
        val asset = assetRetriever.retrieve(url)
            .getOrElse { return Result.failure(IllegalStateException(it.message)) }
        val publication = publicationOpener.open(asset, allowUserInteraction = false)
            .getOrElse {
                asset.close()
                return Result.failure(IllegalStateException(it.message))
            }
        return Result.success(publication)
    }
}
