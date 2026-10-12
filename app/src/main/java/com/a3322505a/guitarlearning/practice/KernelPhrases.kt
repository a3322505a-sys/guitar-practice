package com.a3322505a.guitarlearning.practice

import com.a3322505a.guitarlearning.music.Position

/** Original, standard-tuning exercises: one registration path for every supported expression. */
internal object KernelPhrases {
    private fun phrase(id: String,title: String,technique: String,seq: Sequence,goal: String,use: String="单技巧",related: String?=null)=Phrase(
        id,title,"原创练习","${seq.length/seq.barTicks} 小节","原创；标准调弦，无变调夹",60,"入门至进阶",technique,
        "标准调弦 · 无变调夹",seq,tempoLabel="示范速度",goal=goal,suggestedBpm=40,use=use,relatedId=related)
    private val triplets=phrase("T01","三连音 · 八分与十六分","三连音",Sequence(
        listOf(Event(0,4,listOf(Position(3,5))),Event(4,4,listOf(Position(3,7))),Event(8,4,emptyList()),
            Event(12,2,listOf(Position(2,5))),Event(14,2,listOf(Position(2,6))),Event(16,2,listOf(Position(2,8))),Event(18,6,emptyList())),
        24,"第一拍八分三连音（第三音休止），第二拍前半十六分三连音，后半休止。数字 3 内保持等间隔。",beatsPerBar=2,tuplets=listOf(Tuplet(0,4),Tuplet(12,2))),"三音平均分配时值，遇休止继续数拍。")
    private val grip=listOf(Position(5,0),Position(4,2),Position(3,2))
    private fun rhythm(full: Boolean): Sequence {
        val durations=listOf(6,3,3,6,6,12,6,6,6,3,3,12,6,6,6,6)
        var tick=0
        val events=durations.mapIndexed { i,d ->
            val e=when {
                i in listOf(3,7,13) -> Event(tick,d,emptyList())
                full && i in listOf(2,10) -> Event(tick,d,emptyList(),deadStrings=setOf(5,4,3),pick=Pick.UP)
                full && i==9 -> Event(tick,d,listOf(Position(5,0)),deadStrings=setOf(4,3),strum=true,pick=Pick.UP,velocity=.65f)
                else -> Event(tick,d,grip,strum=true,pick=if(i%2==0) Pick.DOWN else Pick.UP,
                    accent=full && i in listOf(0,8),short=full && i in listOf(5,11),velocity=if(full && i==6) .5f else 1f)
            };tick+=d;e
        }
        return Sequence(events,96,"两小节 Am 节奏练习：十六分、弱拍起音和休止。${if(full) "强拍重音，x 为死音；短促和弦及时收音。上下拨按箭头；第 2 小节含正常弦与死音弦混合。" else "基础版先只弹有音高和弦，按箭头拨弦并保留休止。"}")
    }
    private val basic=phrase("R01","节奏句 · 基础版","十六分／切分／上下拨",rhythm(false),"保持右手拍点，休止时停止起音。","简单和弦节奏")
    private val full=phrase("R02","节奏句 · 完整版","死音／重音／短促／上下拨",rhythm(true),"在基础节奏中分清重音、弱奏、死音和短促收音。","综合片段","R01")
    private val pm=phrase("PM01","P.M. · 普通与制音对照","手掌制音",Sequence(
        listOf(Event(0,12,listOf(Position(6,0))),Event(12,12,listOf(Position(6,0)),palmMute=true),Event(24,12,emptyList(),deadStrings=setOf(6)),Event(36,12,emptyList())),
        48,"依次听普通低 E、P.M. 低 E、死音 x、休止；P.M. 保留 E 音高，死音为打击性弦声。"),"比较普通音、制音、死音与休止。")
    private fun linkPhrase(id: String,name: String,kind: Technique,a: Int,b: Int)=phrase(id,name,if(kind==Technique.SLIDE) "同弦滑音" else if(kind==Technique.HAMMER) "击弦" else "勾弦",Sequence(
        listOf(Event(0,12,listOf(Position(3,a))),Event(12,12,listOf(Position(3,b)),pick=Pick.NONE),Event(24,12,emptyList()),Event(36,12,listOf(Position(3,b))),Event(48,12,listOf(Position(3,a)),pick=Pick.NONE),Event(60,36,emptyList())),
        96,"三弦 $a${if(kind==Technique.SLIDE) (if(b>a) "/" else "\\") else kind.mark}$b；每对只拨来源音一次。第二对跨小节反向返回，目标在下一拍到达。合成连奏仅作技巧示范近似。",
        links=listOf(Legato(0,12,3,kind),Legato(36,48,3,if(kind==Technique.HAMMER) Technique.PULL else if(kind==Technique.PULL) Technique.HAMMER else kind))),"每对只拨一次，目标音准确落在下一拍。")
    val all=listOf(triplets,basic,full,pm,linkPhrase("SL01","滑音 · 5/7 与 7\\5",Technique.SLIDE,5,7),
        linkPhrase("H01","击弦 · 5h7",Technique.HAMMER,5,7),linkPhrase("P01","勾弦 · 7p5",Technique.PULL,7,5))
}
