package cn.edu.traveljournal

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import java.io.File

/** Original code-drawn teaching illustrations, never represented as real travel photos. */
fun loadDemoJournals(context: Context, repo: JournalRepository) {
    val demos=listOf(
        Triple("海风替我按下了暂停键","海边的一天","沿着沙滩走了很久，等太阳慢慢落下。没有赶行程，只想把海风和橙色的天空记下来。"),
        Triple("在山野里，走慢一点","山间步道","清晨沿着小路往前走。风从树叶间穿过，远处的山一层一层展开，脚下的路也变得轻快。"),
        Triple("一条小巷，一整个下午","老城街巷","拐进一条安静的小巷，在临街的小店坐了一会儿。把地图收起来，跟着喜欢的方向慢慢逛。"))
    demos.forEachIndexed { index, data ->
        val id="demo-journal-$index"
        if(repo.journals().any{it.id==id})return@forEachIndexed
        val illustration=File(context.cacheDir,"demo-landscape-$index.jpg")
        val b=Bitmap.createBitmap(1200,800,Bitmap.Config.ARGB_8888)
        val c=Canvas(b)
        val p=Paint(Paint.ANTI_ALIAS_FLAG)
        fun rect(l:Float,t:Float,r:Float,bottom:Float,color:String){p.color=Color.parseColor(color);c.drawRect(l,t,r,bottom,p)}
        fun circle(x:Float,y:Float,r:Float,color:String){p.color=Color.parseColor(color);c.drawCircle(x,y,r,p)}
        when(index){
            0->{
                rect(0f,0f,1200f,800f,"#E7C7AD");circle(835f,265f,100f,"#D88B5B")
                rect(0f,390f,1200f,800f,"#628B89");rect(0f,620f,1200f,800f,"#D2BC98")
                p.color=Color.parseColor("#BACBC0");p.strokeWidth=4f
                listOf(445f,510f,570f).forEach{y->c.drawLine(90f,y,1050f,y+8f,p)}
                p.color=Color.parseColor("#334E49");p.strokeWidth=8f;c.drawLine(218f,385f,218f,490f,p)
                val sail=Path().apply{moveTo(204f,400f);lineTo(142f,463f);lineTo(204f,463f);close()};c.drawPath(sail,p)
            }
            1->{
                rect(0f,0f,1200f,800f,"#D9E1CF");circle(830f,155f,65f,"#D9B98C")
                listOf(Triple(300f,"#A6B99E",100f),Triple(420f,"#789681",-120f),Triple(580f,"#426A58",0f)).forEach{(y,color,offset)->
                    val path=Path().apply{moveTo(0f,y+160f);cubicTo(300f+offset,y-230f,450f,y-100f,660f,y+80f);cubicTo(850f,y-120f,1000f,y-200f,1200f,y+80f);lineTo(1200f,800f);lineTo(0f,800f);close()}
                    p.color=Color.parseColor(color);c.drawPath(path,p)
                }
                val road=Path().apply{moveTo(430f,800f);cubicTo(970f,640f,530f,530f,850f,520f)}
                p.color=Color.parseColor("#DBCCAA");p.style=Paint.Style.STROKE;p.strokeWidth=18f;c.drawPath(road,p);p.style=Paint.Style.FILL
            }
            2->{
                rect(0f,0f,1200f,800f,"#E5DCCB");rect(0f,570f,1200f,800f,"#C0B29C")
                listOf(70f,340f,675f,955f).forEachIndexed{i,x->
                    rect(x,220f+(i%2)*35f,x+230f,600f,if(i%2==0)"#C8AB8F" else "#D4C2A9")
                    val roof=Path().apply{moveTo(x-18f,230f+(i%2)*35f);lineTo(x+110f,155f+(i%2)*35f);lineTo(x+248f,230f+(i%2)*35f);close()}
                    p.color=Color.parseColor("#4C6059");c.drawPath(roof,p)
                    rect(x+85f,430f,x+150f,600f,"#53685F");rect(x+30f,300f,x+75f,355f,"#647A6D");rect(x+155f,300f,x+200f,355f,"#647A6D")
                }
                circle(596f,250f,100f,"#91A58B");rect(587f,305f,600f,610f,"#776B57")
            }
        }
        illustration.outputStream().use{b.compress(Bitmap.CompressFormat.JPEG,92,it)};b.recycle()
        val photo=repo.importPhoto(Uri.fromFile(illustration))
        repo.saveJournal(Journal(id=id,title="${data.first} · 示例",date=listOf("2026-10-18","2026-10-06","2026-09-21")[index],place=data.second,
            body="${data.third}\n\n这是可删除的教学示例，配图是程序绘制的示意插画，不是真实旅行照片。你可以编辑它，或创建自己的旅行记录。",photos=listOf(photo),
            tags=listOf(listOf("海边","日落","放空"),listOf("山野","徒步","自然"),listOf("老城","漫步","街巷"))[index]+"演示记录",
            photoDescription="教学示例插画，非真实目的地实景。",
            latitude=listOf(36.0571,30.1346,25.6892)[index],longitude=listOf(120.3355,118.1659,100.1615)[index],mapSticker=photo,visited=false))
        illustration.delete()
    }
}
