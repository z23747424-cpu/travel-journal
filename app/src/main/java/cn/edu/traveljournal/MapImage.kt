package cn.edu.traveljournal

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File

/** Small local raster copy for map stickers; never loads a remote image or exports EXIF. */
internal fun mapThumbnail(file:File):ByteArray?=runCatching {
    val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
    BitmapFactory.decodeFile(file.path,bounds)
    require(bounds.outWidth in 1..20000&&bounds.outHeight in 1..20000&&bounds.outWidth.toLong()*bounds.outHeight<=60_000_000)
    var sample=1
    while(maxOf(bounds.outWidth,bounds.outHeight)/sample>256)sample*=2
    val raw=BitmapFactory.decodeFile(file.path,BitmapFactory.Options().apply{inSampleSize=sample})?:return@runCatching null
    try {
        val orientation=runCatching{ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION,1)}.getOrDefault(1)
        val oriented=orientBitmap(raw,orientation)
        try{ByteArrayOutputStream().use{out->check(oriented.compress(Bitmap.CompressFormat.PNG,100,out));out.toByteArray()}}
        finally{if(oriented!==raw)oriented.recycle()}
    }finally{raw.recycle()}
}.getOrNull()
