package com.a3322505a.guitarlearning.music

/** Exact pitched grips from the five v68 GP passages. Names describe these played tones. */
object ShayouChords {
    private fun grip(shape: String,sounding: String,root: Int,third: Int?,frets: List<Int?>,fingers: List<Int>,bars: String,
        doubleStop: Boolean = false,dim: Boolean = false,names: Map<Int,String> = emptyMap()) = Chord(
        name="斜阳 · $shape",root=(root+3)%12,third=third?.let { (it+3)%12 },frets=frets,fingers=fingers,
        capoFret=3,soundingName=sounding,group=if(doubleStop) "斜阳 · 双音 riff" else "斜阳 · 伴奏指法",
        source="斜阳TV版.gp · $bars 小节；指法名称按实际组成音归纳，数字相对变调夹，用指为参考。",
        intervalRoles=if(dim) mapOf(6 to "减五音",9 to "减七音") else emptyMap(),toneNames=names)
    val all = listOf(
        grip("Bm7(no5)","Dm7(no5)",11,2,listOf(null,2,0,2,3,null),listOf(0,1,0,2,3,0),"2、4、6、8、18、20、22、24、26"),
        grip("Gsus2","B♭sus2",7,null,listOf(3,null,0,2,3,null),listOf(3,0,0,1,4,0),"2、4、6、8、28",names=mapOf(10 to "B♭")),
        grip("Asus4","Csus4",9,null,listOf(null,0,2,2,3,null),listOf(0,0,1,2,3,0),"3、5、7、9"),
        grip("D5","F5",2,null,listOf(null,null,0,2,3,null),listOf(0,0,0,1,3,0),"3、7、9、19、21、23、25"),
        grip("F♯7(no5)","A7(no5)",6,10,listOf(2,null,2,3,null,null),listOf(1,0,2,3,0,0),"5"),
        grip("G5","B♭5",7,null,listOf(3,null,0,0,null,null),listOf(3,0,0,0,0,0),"18、20、22、24",names=mapOf(10 to "B♭")),
        grip("A5","C5",9,null,listOf(null,0,2,2,null,null),listOf(0,0,1,2,0,0),"18、20、22、24、26"),
        grip("B♭dim7","C♯dim7",10,1,listOf(null,1,2,0,2,null),listOf(0,1,2,0,3,0),"25、26",dim=true,names=mapOf(10 to "B♭")),
        grip("D/F♯","F/A",2,6,listOf(2,null,0,2,3,null),listOf(1,0,0,2,3,0),"28"),
        grip("Dm/F","Fm/A♭",2,5,listOf(1,null,0,2,3,null),listOf(1,0,0,2,3,0),"28",names=mapOf(8 to "A♭")),
        grip("Em7","Gm7",4,7,listOf(0,2,2,0,3,null),listOf(0,2,3,0,4,0),"29–30",names=mapOf(10 to "B♭")),
        grip("D5/A · 高音10品","F5/C",2,null,listOf(null,null,null,null,10,10),listOf(0,0,0,0,1,1),"32–39",doubleStop=true),
        grip("C♯5/G♯ · 9品","E5/B",1,null,listOf(null,null,null,null,9,9),listOf(0,0,0,0,1,1),"32、34、35、36、38",doubleStop=true),
        grip("E5/B · 12品","G5/D",4,null,listOf(null,null,null,null,12,12),listOf(0,0,0,0,1,1),"33、37",doubleStop=true),
        grip("F♯5/C♯ · 14品","A5/E",6,null,listOf(null,null,null,null,14,14),listOf(0,0,0,0,1,1),"37",doubleStop=true),
        grip("D5/A · 低音7品","F5/C",2,null,listOf(null,null,7,7,null,null),listOf(0,0,1,1,0,0),"39",doubleStop=true),
    )
}
