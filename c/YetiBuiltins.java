// ex: se sts=4 sw=4 expandtab:

/*
 * Yeti language compiler java bytecode generator.
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

interface YetiBuiltins extends YetiCode {
    int COND_EQ  = 0;
    int COND_NOT = 1;
    int COND_LT  = 2;
    int COND_GT  = 4;
    int COND_LE  = COND_NOT | COND_GT;
    int COND_GE  = COND_NOT | COND_LT;

    class CoreFun implements Binder {
        private YetiType.Type type;
        private String name;

        CoreFun(YetiType.Type type, String field) {
            this.type = type;
            name = field;
        }

        public BindRef getRef(int line) {
            return new StaticRef("yeti/lang/Core", name,
                                 type, this, true, line);
        }
    }

    class Ignore implements Binder {
        public BindRef getRef(int line) {
            return new StaticRef("yeti/lang/Core", "IGNORE",
                        YetiType.A_TO_UNIT, this, true, line) {
                Code apply(final Code arg1, YetiType.Type res, int line) {
                    return new Code() {
                        { type = YetiType.UNIT_TYPE; }

                        void gen(Ctx ctx) {
                            arg1.gen(ctx);
                        }
                    };
                }
            };
        }
    }

    abstract class Bind2Core implements Binder, Opcodes {
        private String coreFun;
        private YetiType.Type type;

        Bind2Core(String fun, YetiType.Type type) {
            coreFun = fun;
            this.type = type;
        }

        public BindRef getRef(int line) {
            return new StaticRef("yeti/lang/Core", coreFun,
                                 type, this, true, line) {
                Code apply(final Code arg1, YetiType.Type res, int line1) {
                    return new Apply(res, this, arg1, line1) {
                        Code apply(final Code arg2, final YetiType.Type res,
                                   final int line2) {
                            return new Code() {
                                { type = res; }

                                void gen(Ctx ctx) {
                                    genApply2(ctx, arg1, arg2, line2);
                                }
                            };
                        }
                    };
                }
            };
        }

        abstract void genApply2(Ctx ctx, Code arg1, Code arg2, int line);
    }

    class For extends Bind2Core {
        For() {
            super("FOR", YetiType.FOR_TYPE);
        }

        void genApply2(Ctx ctx, Code list, Code fun, int line) {
            Label nop = new Label(), end = new Label();
            list.gen(ctx);
            fun.gen(ctx);
            ctx.visitLine(line);
            ctx.m.visitInsn(SWAP);
            ctx.m.visitInsn(DUP);
            ctx.m.visitJumpInsn(IFNULL, nop);
            ctx.m.visitTypeInsn(CHECKCAST, "yeti/lang/AList");
            ctx.m.visitInsn(DUP);
            ctx.m.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/AList",
                                  "iter", "()Lyeti/lang/ListIter;");
            ctx.m.visitInsn(DUP_X2);
            ctx.m.visitInsn(POP);
            ctx.m.visitMethodInsn(INVOKEINTERFACE, "yeti/lang/ListIter",
                    "forEach", "(Ljava/lang/Object;Lyeti/lang/AIter;)V");
            ctx.m.visitJumpInsn(GOTO, end);
            ctx.m.visitLabel(nop);
            ctx.m.visitInsn(POP2);
            ctx.m.visitLabel(end);
            ctx.m.visitInsn(ACONST_NULL);
        }
    }

    class Compose extends Bind2Core {
        Compose() {
            super("COMPOSE", YetiType.COMPOSE_TYPE);
        }

        void genApply2(Ctx ctx, Code arg1, Code arg2, int line) {
            ctx.m.visitTypeInsn(NEW, "yeti/lang/Compose");
            ctx.m.visitInsn(DUP);
            arg1.gen(ctx);
            arg2.gen(ctx);
            ctx.visitLine(line);
            ctx.m.visitMethodInsn(INVOKESPECIAL, "yeti/lang/Compose",
                    "<init>", "(Ljava/lang/Object;Ljava/lang/Object;)V");
        }
    }

    abstract class BinOpRef extends BindRef implements DirectBind {
        boolean markTail2;
        String coreFun;

        Code apply2nd(final Code arg2, final YetiType.Type t, final int line) {
            return new Code() {
                { type = t; }

                void gen(Ctx ctx) {
                    ctx.visitLine(line);
                    BinOpRef.this.gen(ctx);
                    arg2.gen(ctx);
                    ctx.m.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/BinFun",
                        "apply2nd", "(Ljava/lang/Object;)Lyeti/lang/Fun;");
                }
            };
        }

        Code apply(final Code arg1, final YetiType.Type res1, final int line) {
            return new Code() {
                { type = res1; }

                Code apply(final Code arg2, final YetiType.Type res, int line) {
                    return new Code() {
                        { type = res; }

                        void gen(Ctx ctx) {
                            binGen(ctx, arg1, arg2);
                        }

                        void genIf(Ctx ctx, Label to, boolean ifTrue) {
                            binGenIf(ctx, arg1, arg2, to, ifTrue);
                        }

                        void markTail() {
                            if (markTail2) {
                                arg2.markTail();
                            }
                        }
                    };
                }

                void gen(Ctx ctx) {
                    BinOpRef.this.gen(ctx);
                    arg1.gen(ctx);
                    ctx.visitLine(line);
                    ctx.m.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/Fun",
                        "apply", "(Ljava/lang/Object;)Ljava/lang/Object;");
                }
            };
        }

        void gen(Ctx ctx) {
            ctx.m.visitFieldInsn(GETSTATIC, "yeti/lang/Core",
                                 coreFun, "Lyeti/lang/BinFun;");
        }

        abstract void binGen(Ctx ctx, Code arg1, Code arg2);

        void binGenIf(Ctx ctx, Code arg1, Code arg2,
                Label to, boolean ifTrue) {
            throw new UnsupportedOperationException("binGenIf");
        }
    }

    class ArithOpFun extends BinOpRef implements Binder {
        String method;

        public ArithOpFun(String method, YetiType.Type type) {
            this.type = type;
            this.method = method;
            binder = this;
            coreFun = method.toUpperCase() + "_OP";
        }

        public BindRef getRef(int line) {
            return this; // XXX should copy for type?
        }

        void binGen(Ctx ctx, Code arg1, Code arg2) {
            arg1.gen(ctx);
            ctx.m.visitTypeInsn(CHECKCAST, "yeti/lang/Num");
            boolean ii = method == "intDiv" || method == "rem";
            if (arg2 instanceof NumericConstant &&
                ((NumericConstant) arg2).genInt(ctx, ii)) {
                ctx.m.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/Num",
                    method, ii ? "(I)Lyeti/lang/Num;" : "(J)Lyeti/lang/Num;");
                return;
            }
            arg2.gen(ctx);
            ctx.m.visitTypeInsn(CHECKCAST, "yeti/lang/Num");
            ctx.m.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/Num",
                    method, "(Lyeti/lang/Num;)Lyeti/lang/Num;");
        }
    }

    abstract class BoolBinOp extends BinOpRef {
        void binGen(Ctx ctx, Code arg1, Code arg2) {
            Label label = new Label();
            binGenIf(ctx, arg1, arg2, label, false);
            ctx.genBoolean(label);
        }
    }

    class CompareFun extends BoolBinOp {
        static final int[] OPS = { IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE };
        static final int[] ROP = { IFEQ, IFNE, IFGT, IFLE, IFLT, IFGE };
        int op;
        int line;

        void binGenIf(Ctx ctx, Code arg1, Code arg2,
                Label to, boolean ifTrue) {
            YetiType.Type t = arg1.type.deref();
            int op = this.op;
            if (!ifTrue) {
                op ^= COND_NOT;
            }
            Label nojmp = null;
            if (t.type == YetiType.VAR || t.type == YetiType.MAP &&
                    t.param[2] == YetiType.LIST_TYPE &&
                    t.param[1] != YetiType.NUM_TYPE) {
                Label nonull = new Label();
                nojmp = new Label();
                arg2.gen(ctx);
                arg1.gen(ctx); // 2-1
                ctx.visitLine(line);
                ctx.m.visitInsn(DUP); // 2-1-1
                ctx.m.visitJumpInsn(IFNONNULL, nonull); // 2-1
                // reach here, when 1 was null
                if (op == COND_GT || op == COND_LE ||
                    arg2.isEmptyList() && (op == COND_EQ || op == COND_NOT)) {
                    // null is never greater and always less or equal
                    ctx.m.visitInsn(POP2);
                    ctx.m.visitJumpInsn(GOTO,
                        op == COND_LE || op == COND_EQ ? to : nojmp);
                } else {
                    ctx.m.visitInsn(POP); // 2
                    ctx.m.visitJumpInsn(op == COND_EQ || op == COND_GE
                                        ? IFNULL : IFNONNULL, to);
                    ctx.m.visitJumpInsn(GOTO, nojmp);
                }
                ctx.m.visitLabel(nonull);
                ctx.m.visitInsn(SWAP); // 1-2
            } else {
                arg1.gen(ctx);
                if (arg2.isIntNum()) {
                    ctx.m.visitTypeInsn(CHECKCAST, "yeti/lang/Num");
                    ((NumericConstant) arg2).genInt(ctx, false);
                    ctx.visitLine(line);
                    ctx.m.visitMethodInsn(INVOKEVIRTUAL,
                            "yeti/lang/Num", "rCompare", "(J)I");
                    ctx.m.visitJumpInsn(ROP[op], to);
                    return;
                }
                arg2.gen(ctx);
                ctx.visitLine(line);
            }
            if ((op & (COND_LT | COND_GT)) == 0) {
                op ^= COND_NOT;
                ctx.m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object",
                                      "equals", "(Ljava/lang/Object;)Z");
            } else {
                ctx.m.visitMethodInsn(INVOKEINTERFACE, "java/lang/Comparable",
                                      "compareTo", "(Ljava/lang/Object;)I");
            }
            ctx.m.visitJumpInsn(OPS[op], to);
            if (nojmp != null) {
                ctx.m.visitLabel(nojmp);
            }
        }
    }

    class Compare implements Binder {
        static final String[] FUN = { "EQ", "NE", "LT", "GE", "GT", "LE" };
        YetiType.Type type;
        int op;

        public Compare(YetiType.Type type, int op) {
            this.op = op;
            this.type = type;
        }

        public BindRef getRef(int line) {
            CompareFun c = new CompareFun();
            c.binder = this;
            c.type = type;
            c.op = op;
            c.polymorph = true;
            c.line = line;
            c.coreFun = FUN[op];
            return c;
        }
    }

    class InOpFun extends BoolBinOp {
        int line;

        void binGenIf(Ctx ctx, Code arg1, Code arg2,
                Label to, boolean ifTrue) {
            arg2.gen(ctx);
            ctx.m.visitTypeInsn(CHECKCAST, "yeti/lang/Hash");
            arg1.gen(ctx);
            ctx.visitLine(line);
            ctx.m.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/Hash",
                                  "containsKey", "(Ljava/lang/Object;)Z");
            ctx.m.visitJumpInsn(ifTrue ? IFNE : IFEQ, to);
        }
    }

    class InOp implements Binder {
        public BindRef getRef(int line) {
            InOpFun f = new InOpFun();
            f.type = YetiType.IN_TYPE;
            f.line = line;
            return f;
        }
    }

    class NotOp implements Binder {
        public BindRef getRef(int line) {
            return new StaticRef("yeti/lang/Core", "NOT",
                                 YetiType.BOOL_TO_BOOL, this, false, line) {
                Code apply(final Code arg, YetiType.Type res, int line) {
                    return new Code() {
                        { type = YetiType.BOOL_TYPE; }

                        void genIf(Ctx ctx, Label to, boolean ifTrue) {
                            arg.genIf(ctx, to, !ifTrue);
                        }

                        void gen(Ctx ctx) {
                            Label label = new Label();
                            arg.genIf(ctx, label, true);
                            ctx.genBoolean(label);
                        }
                    };
                }
            };
        }
    }

    class BoolOpFun extends BoolBinOp implements Binder {
        boolean orOp;

        BoolOpFun(boolean orOp) {
            this.type = YetiType.BOOLOP_TYPE;
            this.orOp = orOp;
            binder = this;
            markTail2 = true;
            coreFun = orOp ? "OR_OP" : "AND_OP";
        }

        public BindRef getRef(int line) {
            return this;
        }

        void binGen(Ctx ctx, Code arg1, Code arg2) {
            if (arg2 instanceof CompareFun) {
                super.binGen(ctx, arg1, arg2);
            } else {
                Label label = new Label(), end = new Label();
                arg1.genIf(ctx, label, orOp);
                arg2.gen(ctx);
                ctx.m.visitJumpInsn(GOTO, end);
                ctx.m.visitLabel(label);
                ctx.m.visitFieldInsn(GETSTATIC, "java/lang/Boolean",
                        orOp ? "TRUE" : "FALSE", "Ljava/lang/Boolean;");
                ctx.m.visitLabel(end);
            }
        }

        void binGenIf(Ctx ctx, Code arg1, Code arg2,
                      Label to, boolean ifTrue) {
            if (orOp == ifTrue) {
                arg1.genIf(ctx, to, orOp);
                arg2.genIf(ctx, to, orOp);
            } else {
                Label noJmp = new Label();
                arg1.genIf(ctx, noJmp, orOp);
                arg2.genIf(ctx, to, !orOp);
                ctx.m.visitLabel(noJmp);
            }
        }
    }

    class Cons implements Binder {
        public BindRef getRef(final int line) {
            return new BinOpRef() {
                {
                    type = YetiType.CONS_TYPE;
                    binder = Cons.this;
                    coreFun = "CONS";
                    polymorph = true;
                }

                void binGen(Ctx ctx, Code arg1, Code arg2) {
                    ctx.visitLine(line);
                    ctx.m.visitTypeInsn(NEW, "yeti/lang/LList");
                    ctx.m.visitInsn(DUP);
                    arg1.gen(ctx);
                    arg2.gen(ctx);
                    ctx.visitLine(line);
                    ctx.m.visitTypeInsn(CHECKCAST, "yeti/lang/AList");
                    ctx.m.visitMethodInsn(INVOKESPECIAL, "yeti/lang/LList",
                        "<init>", "(Ljava/lang/Object;Lyeti/lang/AList;)V");
                }
            };
        }
    }

    class MatchOpFun extends BinOpRef implements Binder {
        MatchOpFun() {
            type = YetiType.STR2_PRED_TYPE;
            coreFun = "MATCH_OP";
        }

        public BindRef getRef(int line) {
            return this;
        }

        void binGen(Ctx ctx, Code arg1, final Code arg2) {
            apply2nd(arg2, YetiType.STR2_PRED_TYPE, 0).gen(ctx);
            arg1.gen(ctx);
            ctx.m.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/Fun",
                    "apply", "(Ljava/lang/Object;)Ljava/lang/Object;");
        }

        Code apply2nd(final Code arg2, final YetiType.Type t, final int line) {
            final Code matcher = new Code() {
                { type = t; }

                void gen(Ctx ctx) {
                    ctx.m.visitTypeInsn(NEW, "yeti/lang/Match");
                    ctx.m.visitInsn(DUP);
                    arg2.gen(ctx);
                    ctx.m.visitMethodInsn(INVOKESPECIAL, "yeti/lang/Match",
                                          "<init>", "(Ljava/lang/Object;)V");
                }
            };
            if (!(arg2 instanceof StringConstant)) {
                return matcher;
            }
            return new Code() {
                { type = t; }

                void gen(Ctx ctx) {
                    ctx.constant("MATCH-FUN:".concat(((StringConstant) arg2)
                                    .str), matcher);
                }
            };
        }
    }
}