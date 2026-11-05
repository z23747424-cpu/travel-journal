package cn.edu.traveljournal

import android.content.Context
import android.net.Uri
import android.util.LruCache
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.io.File

internal val MAP_FILES=setOf("index.html","map.js","map.css","china-boundaries.js","vendor/leaflet.js","vendor/leaflet.css")

/** No network is needed for the complete country map or the user's illustration copies. */
internal class LocalMapResources(private val context:Context) {
    private val images=object:LruCache<String,ByteArray>(4*1024*1024){override fun sizeOf(key:String,value:ByteArray)=value.size}
    fun intercept(uri:Uri,method:String,isMainFrame:Boolean):WebResourceResponse? {
        if(uri.scheme=="https"&&uri.host=="appassets.androidplatform.net"&&method=="GET") {
            if(uri.path.orEmpty().startsWith("/photos/")) {
                val name=uri.path.orEmpty().removePrefix("/photos/")
                if(PHOTO_NAME.matches(name)) {
                    val directory=File(context.filesDir,"photos")
                    val file=File(directory,name)
                    if(file.isFile&&file.canonicalFile.parentFile==directory.canonicalFile) {
                        val key=name+":"+file.lastModified()
                        val bytes=images.get(key)?:mapThumbnail(file)?.also{images.put(key,it)}
                        if(bytes!=null)return WebResourceResponse("image/png",null,200,"OK",mapOf("Cache-Control" to "private, max-age=86400"),ByteArrayInputStream(bytes))
                    }
                }
                return denied(404,"Not Found")
            }
            val path=uri.path.orEmpty()
            val name=path.removePrefix("/assets/map/")
            if(path.startsWith("/assets/map/")&&name in MAP_FILES) {
                val mime=when {name.endsWith(".js")->"application/javascript";name.endsWith(".css")->"text/css";else->"text/html"}
                return WebResourceResponse(mime,"UTF-8",context.assets.open("map/$name"))
            }
        }
        if(uri.scheme=="https"&&uri.host=="tile.openstreetmap.org"&&!isMainFrame&&method=="GET")return null
        return denied(403,"Forbidden")
    }
    private fun denied(status:Int,reason:String)=WebResourceResponse("text/plain","UTF-8",status,reason,emptyMap(),ByteArrayInputStream(ByteArray(0)))
}
