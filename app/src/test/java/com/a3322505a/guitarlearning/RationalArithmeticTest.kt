package com.a3322505a.guitarlearning

import com.a3322505a.guitarlearning.music.score.Q
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger
import kotlin.random.Random

/** Independent BigInteger oracle for exact fast arithmetic, bounds and overflow fallback. */
class RationalArithmeticTest {
    private val limit=BigInteger.valueOf(Q.LIMIT)
    private fun check(n: BigInteger,d: BigInteger, actual: ()->Q) {
        val sign=if(d.signum()<0) BigInteger.valueOf(-1) else BigInteger.ONE
        val gcd=n.gcd(d);val nn=n/gcd*sign;val dd=d/gcd*sign
        val result=runCatching(actual)
        if(nn.abs()>limit || dd>limit) assertTrue("Must reject $nn/$dd",result.isFailure)
        else {
            val q=result.getOrThrow()
            assertEquals(nn.toLong(),q.n);assertEquals(dd.toLong(),q.d)
        }
    }
    @Test fun signedAndOverflowingIntermediatesMatchIndependentExactFractions() {
        val random=Random(3104)
        val edge=listOf(Q.ZERO,Q(1),Q(-1),Q(Q.LIMIT),Q(-Q.LIMIT),Q(Q.LIMIT,Q.LIMIT-1),Q(Q.LIMIT-1,Q.LIMIT),Q(1,Q.LIMIT))
        val values=edge+List(90) {
            if(it%2==0) Q(random.nextLong(-1000000,1000001),random.nextLong(1,25))
            else Q(random.nextLong(-Q.LIMIT,Q.LIMIT),random.nextLong(1,Q.LIMIT))
        }
        for(a in values) for(b in values) {
            val an=BigInteger.valueOf(a.n);val ad=BigInteger.valueOf(a.d)
            val bn=BigInteger.valueOf(b.n);val bd=BigInteger.valueOf(b.d)
            check(an*bd+bn*ad,ad*bd) { a+b }
            check(an*bd-bn*ad,ad*bd) { a-b }
            check(an*bn,ad*bd) { a*b }
            if(b.n!=0L) check(an*bd,ad*bn) { a/b }
            assertEquals((an*bd).compareTo(bn*ad).sign,a.compareTo(b).sign)
        }
    }
    @Test fun normalizationKeepsLongMinZeroAndCancellableLargeProductsExact() {
        assertEquals(Q(1),Q(Long.MAX_VALUE,Long.MAX_VALUE))
        assertEquals(Q.ZERO,Q(0,Long.MAX_VALUE))
        assertEquals(Q(-549755813888L),Q(Long.MIN_VALUE,16777216))
        val a=Q(Q.LIMIT,Q.LIMIT-1);val b=Q(Q.LIMIT-1,Q.LIMIT)
        assertEquals(Q(1),a*b)
        assertEquals(Q(1),a/a)
        assertTrue(runCatching { Q(Long.MIN_VALUE) }.isFailure)
        assertTrue(runCatching { Q(1,0) }.isFailure)
        assertTrue(runCatching { Q(1)/Q.ZERO }.isFailure)
    }
    private val Int.sign get() = compareTo(0)
}
