package com.heirloom.server

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ContentTypeExtensionsTest {

    // Known image types
    @Test fun `jpeg maps to jpg`() = assertEquals("jpg", mimeTypeToExtension("image/jpeg"))
    @Test fun `image-jpg alias maps to jpg`() = assertEquals("jpg", mimeTypeToExtension("image/jpg"))
    @Test fun `png maps to png`() = assertEquals("png", mimeTypeToExtension("image/png"))
    @Test fun `gif maps to gif`() = assertEquals("gif", mimeTypeToExtension("image/gif"))
    @Test fun `webp maps to webp`() = assertEquals("webp", mimeTypeToExtension("image/webp"))
    @Test fun `heic maps to heic`() = assertEquals("heic", mimeTypeToExtension("image/heic"))

    // Known video types
    @Test fun `mp4 maps to mp4`() = assertEquals("mp4", mimeTypeToExtension("video/mp4"))
    @Test fun `quicktime maps to mov`() = assertEquals("mov", mimeTypeToExtension("video/quicktime"))
    @Test fun `x-msvideo maps to avi`() = assertEquals("avi", mimeTypeToExtension("video/x-msvideo"))
    @Test fun `webm maps to webm`() = assertEquals("webm", mimeTypeToExtension("video/webm"))
    @Test fun `3gpp maps to 3gp`() = assertEquals("3gp", mimeTypeToExtension("video/3gpp"))

    // Fallback behaviour
    @Test fun `octet-stream maps to bin`() = assertEquals("bin", mimeTypeToExtension("application/octet-stream"))
    @Test fun `unknown type falls back to subtype word`() = assertEquals("xyz", mimeTypeToExtension("application/xyz"))
    @Test fun `unknown complex subtype falls back to bin`() = assertEquals("bin", mimeTypeToExtension("application/vnd.some+thing"))

    // Content-Type with charset parameter is handled
    @Test fun `mime type with charset parameter is stripped`() =
        assertEquals("jpg", mimeTypeToExtension("image/jpeg; charset=utf-8"))

    // Case insensitivity
    @Test fun `mime type is case insensitive`() = assertEquals("mp4", mimeTypeToExtension("VIDEO/MP4"))
}
