package com.a3322505a.guitarlearning.practice.compile

/** Immutable augmented interval tree; a long bass does not turn a point query into a full scan. */
class IntervalIndex<T>(items: List<T>, private val start: (T)->Double, private val end: (T)->Double) {
    private val sorted=items.sortedBy(start)
    private val maximum=DoubleArray(maxOf(1,sorted.size*4)) { Double.NEGATIVE_INFINITY }
    init { if(sorted.isNotEmpty()) build(1,0,sorted.lastIndex) }
    private fun build(node: Int,lo: Int,hi: Int): Double {
        maximum[node]=if(lo==hi) end(sorted[lo]) else {
            val mid=(lo+hi)/2; maxOf(build(node*2,lo,mid),build(node*2+1,mid+1,hi))
        }
        return maximum[node]
    }
    fun query(a: Double,b: Double): List<T> {
        val result=mutableListOf<T>()
        fun visit(node: Int,lo: Int,hi: Int) {
            if(maximum[node]<=a || start(sorted[lo])>=b) return
            if(lo==hi) { result+=sorted[lo]; return }
            val mid=(lo+hi)/2;visit(node*2,lo,mid);visit(node*2+1,mid+1,hi)
        }
        if(sorted.isNotEmpty()) visit(1,0,sorted.lastIndex)
        return result
    }
}
