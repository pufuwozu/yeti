// ex: se sts=4 sw=4 expandtab:

/**
 * Yeti core library - Number interface.
 *
 * Copyright (c) 2007 Madis Janson
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. The name of the author may not be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package yeti.lang;

import java.math.BigInteger;

public final class BigNum extends Num {
    private final BigInteger v;

    public BigNum(long num) {
        v = BigInteger.valueOf(num);
    }

    public BigNum(BigInteger num) {
        v = num;
    }

    public Num add(Num num) {
        return num.add(v);
    }

    public Num add(RatNum num) {
        return num.add(v);
    }

    public Num add(BigInteger num) {
        return new BigNum(num.add(v));
    }

    public Num add(long num) {
        return new BigNum(v.add(BigInteger.valueOf(num)));
    }

    public Num mul(Num num) {
        return num.mul(v);
    }

    public Num mul(long num) {
        return new BigNum(v.multiply(BigInteger.valueOf(num)));
    }

    public Num mul(RatNum num) {
        return num.mul(v);
    }

    public Num mul(BigInteger num) {
        return new BigNum(num.multiply(v));
    }

    public Num div(Num num) {
        return new FloatNum(v.doubleValue() / num.doubleValue());
    }

    public Num div(long num) {
        return new FloatNum(v.doubleValue() / num);
    }

    public Num divFrom(long num) {
        return new FloatNum((double) num / v.doubleValue());
    }

    public Num divFrom(RatNum num) {
        return new FloatNum(num.doubleValue() / v.doubleValue());
    }

    public Num intDiv(Num num) {
        return num.intDivFrom(v);
    }

    public Num intDivFrom(BigInteger num) {
        return new BigNum(num.divide(v));
    }

    public Num intDivFrom(long num) {
        return new IntNum(BigInteger.valueOf(num).divide(v).longValue());
    }

    public Num sub(Num num) {
        return num.subFrom(v);
    }

    public Num sub(long num) {
        return new BigNum(v.subtract(BigInteger.valueOf(num)));
    }

    public Num subFrom(long num) {
        return new BigNum(BigInteger.valueOf(num).subtract(v));
    }

    public Num subFrom(RatNum num) {
        return new FloatNum(num.doubleValue() - v.doubleValue());
    }

    public Num subFrom(BigInteger num) {
        return new BigNum(num.subtract(v));
    }

    public byte byteValue() {
        return v.byteValue();
    }

    public short shortValue() {
        return v.shortValue();
    }

    public int intValue() {
        return v.intValue();
    }

    public long longValue() {
        return v.longValue();
    }

    public float floatValue() {
        return v.floatValue();
    }

    public double doubleValue() {
        return v.doubleValue();
    }

    public int compareTo(Object num) {
        return ((Num) num).compareTo(v);
    }

    public int compareTo(long num) {
        return v.compareTo(BigInteger.valueOf(num));
    }

    public int compareTo(RatNum num) {
        return -num.compareTo(v);
    }

    public int compareTo(BigInteger num) {
        return v.compareTo(num);
    }

    public String toString() {
        return v.toString();
    }

    public int hashCode() {
        if (v.bitLength() > 63) {
            return v.hashCode();
        }
        // for values in long's range return same as IntNum or Long
        long x = v.longValue();
        return (int) (x ^ (x >>> 32));
    }
}