// ex: se sts=4 sw=4 expandtab:

/*
 * Yeti language compiler java bytecode generator for java foreign interface.
 *
 * Copyright (c) 2007,2008 Madis Janson
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

package yeti.lang.compiler;

import org.objectweb.asm.*;

abstract class JavaExpr extends YetiCode.Code implements YetiCode {
    JavaType.Method method;
    Code[] args;
    int line;

    JavaExpr(JavaType.Method method, Code[] args, int line) {
        this.method = method;
        this.args = args;
        this.line = line;
    }

    private void convert(Ctx ctx, YetiType.Type given,
                         YetiType.Type argType) {
        String descr = argType.javaType == null
                        ? "" : argType.javaType.description;
        if (argType.type == YetiType.JAVA_ARRAY ||
            argType.type == YetiType.JAVA &&
                argType.javaType.isCollection()) {
            Label retry = new Label(), end = new Label();
            ctx.m.visitTypeInsn(CHECKCAST, "yeti/lang/AIter"); // i
            String tmpClass = descr != "Ljava/lang/Set;"
                ? "java/util/ArrayList" : "java/util/HashSet";
            ctx.m.visitTypeInsn(NEW, tmpClass); // ia
            ctx.m.visitInsn(DUP);               // iaa
            ctx.m.visitMethodInsn(INVOKESPECIAL, tmpClass,
                                  "<init>", "()V"); // ia
            ctx.m.visitInsn(SWAP); // ai
            ctx.m.visitInsn(DUP); // aii
            ctx.m.visitJumpInsn(IFNULL, end); // ai
            ctx.m.visitInsn(DUP); // aii
            ctx.m.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/AIter",
                                  "isEmpty", "()Z"); // aiz
            ctx.m.visitJumpInsn(IFNE, end); // ai
            ctx.m.visitLabel(retry);
            ctx.m.visitInsn(DUP2); // aiai
            ctx.m.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/AIter",
                                  "first", "()Ljava/lang/Object;");
            YetiType.Type t = null;
            if (argType.param.length != 0 &&
                ((t = argType.param[0]).type != YetiType.JAVA ||
                 t.javaType.description.length() > 1)) {
                convert(ctx, given.param[0], argType.param[0]);
            }
            // aiav
            ctx.m.visitMethodInsn(INVOKEVIRTUAL, tmpClass,
                                  "add", "(Ljava/lang/Object;)Z"); // aiz
            ctx.m.visitInsn(POP); // ai
            ctx.m.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/AIter",
                                  "next", "()Lyeti/lang/AIter;"); // ai
            ctx.m.visitInsn(DUP); // aii
            ctx.m.visitJumpInsn(IFNONNULL, retry); // ai
            ctx.m.visitLabel(end);
            ctx.m.visitInsn(POP); // a
            if (argType.type != YetiType.JAVA_ARRAY)
                return; // a - List/Set

            String s = "";
            while ((argType = argType.param[0]).type ==
                        YetiType.JAVA_ARRAY) {
                s += "[";
            }
            String arrayPrefix = s;
            if (s == "") {
                s = argType.javaType.className();
            } else {
                s += argType.javaType.description;
            }
            ctx.m.visitInsn(DUP); // aa
            ctx.m.visitMethodInsn(INVOKEVIRTUAL, tmpClass,
                                  "size", "()I"); // an

            descr = t.javaType.description;
            if (t.type != YetiType.JAVA || descr.length() != 1) {
                ctx.m.visitTypeInsn(ANEWARRAY, s); // aA
                ctx.m.visitMethodInsn(INVOKEVIRTUAL,
                    tmpClass, "toArray",
                    "([Ljava/lang/Object;)[Ljava/lang/Object;");
                if (!s.equals("java/lang/Object")) {
                    ctx.m.visitTypeInsn(CHECKCAST,
                        arrayPrefix + "[" + argType.javaType.description);
                }
                return; // A - object array
            }

            // emulate a fucking for loop to fill primitive array
            int index = ctx.localVarCount++;
            Label next = new Label(), done = new Label();
            ctx.m.visitInsn(DUP); // ann
            ctx.m.visitVarInsn(ISTORE, index); // an
            ctx.m.visitTypeInsn(ANEWARRAY, s); // aA
            ctx.m.visitInsn(SWAP); // Aa
            ctx.m.visitLabel(next);
            ctx.m.visitVarInsn(ILOAD, index); // Aan
            ctx.m.visitJumpInsn(IFEQ, done); // Aa
            ctx.intConst(-1); // Aa1
            ctx.m.visitVarInsn(IINC, index); // Aa
            ctx.m.visitInsn(DUP2); // AaAa
            ctx.m.visitVarInsn(ILOAD, index); // AaAan
            ctx.m.visitMethodInsn(INVOKEVIRTUAL, tmpClass,
                                  "get", "(I)Ljava/lang/Object;"); // AaAv
            if (descr == "Z") {
                ctx.m.visitTypeInsn(CHECKCAST, "java/lang/Boolean");
                ctx.m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Boolean",
                                      "booleanValue", "()Z");
            } else {
                ctx.m.visitTypeInsn(CHECKCAST, "yeti/lang/Num");
                convertNum(ctx, descr);
            }
            ctx.m.visitVarInsn(ILOAD, index); // AaAvn
            ctx.m.visitInsn(SWAP); // AaAnv
            int insn = BASTORE;
            switch (argType.javaType.description.charAt(0)) {
                case 'D': insn = DASTORE; break;
                case 'F': insn = FASTORE; break;
                case 'I': insn = IASTORE; break;
                case 'J': insn = LASTORE; break;
                case 'S': insn = SASTORE;
            }
            ctx.m.visitInsn(insn); // Aa
            ctx.m.visitJumpInsn(GOTO, next); // Aa
            ctx.m.visitLabel(done);
            ctx.m.visitInsn(POP); // A
            return; // A - primitive array
        }

        if (given.type != YetiType.NUM ||
            descr == "Ljava/lang/Object;" ||
            descr == "Ljava/lang/Number;")
            return;
        // Convert numbers...
        ctx.m.visitTypeInsn(CHECKCAST, "yeti/lang/Num");
        if (descr == "Ljava/math/BigInteger;") {
            ctx.m.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/Num",
                    "toBigInteger", "()Ljava/math/BigInteger;");
            return;
        }
        if (descr == "Ljava/math/BigDecimal;") {
            ctx.m.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/Num",
                    "toBigDecimal", "()Ljava/math/BigDecimal;");
            return;
        }
        String newInstr = null;
        if (descr.startsWith("Ljava/lang/")) {
            newInstr = argType.javaType.className();
            ctx.m.visitTypeInsn(NEW, newInstr);
            ctx.m.visitInsn(DUP);
            descr = descr.substring(11, 12);
        }
        convertNum(ctx, descr);
        if (newInstr != null) {
            ctx.m.visitMethodInsn(INVOKESPECIAL, newInstr,
                                  "<init>", "(" + descr + ")V");
        }
    }

    private void convertNum(Ctx ctx, String descr) {
        String method = null;
        switch (descr.charAt(0)) {
            case 'B': method = "byteValue"; break;
            case 'D': method = "doubleValue"; break;
            case 'F': method = "floatValue"; break;
            case 'I': method = "intValue"; break;
            case 'L':
            case 'J': method = "longValue"; break;
            case 'S': method = "shortValue"; break;
        }
        ctx.m.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/Num",
                              method, "()" + descr);
    }

    void genCall(Ctx ctx, int invokeInsn) {
        String name = method.classType.javaType.className();
    genargs:
        for (int i = 0; i < args.length; ++i) {
            YetiType.Type given = args[i].type;
            YetiType.Type argType = method.arguments[i];
            String descr =
                argType.javaType == null ? null : argType.javaType.description;
            if (descr == "Z") {
                // boolean
                Label end = new Label(), lie = new Label();
                args[i].genIf(ctx, lie, false);
                ctx.intConst(1);
                ctx.m.visitJumpInsn(GOTO, end);
                ctx.m.visitLabel(lie);
                ctx.intConst(0);
                ctx.m.visitLabel(end);
                continue;
            }
            args[i].gen(ctx);
            if (given.type == YetiType.JAVA) {
                continue;
            }
            ctx.visitLine(line);
            if (descr == "C") {
                ctx.m.visitTypeInsn(CHECKCAST, "java/lang/String");
                ctx.intConst(0);
                ctx.m.visitMethodInsn(INVOKEVIRTUAL,
                        "java/lang/String", "charAt", "(I)C");
                continue;
            }
            if (argType.type == YetiType.JAVA_ARRAY &&
                given.type == YetiType.STR) {
                ctx.m.visitTypeInsn(CHECKCAST, "java/lang/String");
                ctx.m.visitMethodInsn(INVOKEVIRTUAL,
                    "java/lang/String", "toCharArray", "()[C");
                continue;
            }
            convert(ctx, given, argType);
        }
        ctx.visitLine(line);
        ctx.m.visitMethodInsn(invokeInsn, name, method.name, method.descr());
    }
}