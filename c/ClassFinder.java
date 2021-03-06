// ex: se sts=4 sw=4 expandtab:

/*
 * Yeti language compiler java class type reader.
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import java.util.Enumeration;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import yeti.renamed.asm3.ClassReader;

abstract class ClassPathItem {
    abstract InputStream getStream(String name) throws IOException;
    abstract boolean exists(String name);
}

class ClassDir extends ClassPathItem {
    String path;

    ClassDir(String path) {
        this.path = path.length() > 0 ? path.concat(File.separator) : "";
    }

    InputStream getStream(String name) throws IOException {
        return new FileInputStream(path.concat(name));
    }

    boolean exists(String name) {
        return new File(path.concat(name)).isFile();
    }
}

class ClassJar extends ClassPathItem {
    JarFile jar;
    Map entries = Collections.EMPTY_MAP;

    ClassJar(String path) {
        try {
            jar = new JarFile(path);
            Enumeration e = jar.entries();
            entries = new HashMap();
            while (e.hasMoreElements()) {
                ZipEntry entry = (ZipEntry) e.nextElement();
                String name = entry.getName();
                if (name.endsWith(".class"))
                    entries.put(name, entry);
            }
        } catch (IOException ex) {
        }
    }

    InputStream getStream(String name) throws IOException {
        ZipEntry entry = (ZipEntry) entries.get(name);
        return entry == null ? null : jar.getInputStream(entry);
    }

    boolean exists(String name) {
        return entries.containsKey(name);
    }
}

class ClassFinder {
    private ClassPathItem[] classPath;
    private Map defined = new HashMap();
    final Map parsed = new HashMap();
    final Map existsCache = new HashMap();
    final String pathStr;

    ClassFinder(String cp) {
        this(cp.split(File.pathSeparator));
    }

    ClassFinder(String[] cp) {
        classPath = new ClassPathItem[cp.length];
        StringBuffer buf = new StringBuffer();
        for (int i = 0; i < cp.length; ++i) {
            classPath[i] = cp[i].endsWith(".jar")
                ? (ClassPathItem) new ClassJar(cp[i]) : new ClassDir(cp[i]);
            if (i != 0)
                buf.append(File.pathSeparator);
            buf.append(cp[i]);
        }
        pathStr = buf.toString();
    }

    public InputStream findClass(String name) {
        Object x = defined.get(name);
        if (x != null) {
            return new ByteArrayInputStream((byte[]) x);
        }
        InputStream in;
        for (int i = 0; i < classPath.length; ++i) {
            try {
                if ((in = classPath[i].getStream(name)) != null)
                    return in;
            } catch (IOException ex) {
            }
        }
        ClassLoader clc = Thread.currentThread().getContextClassLoader();
        in = clc != null ? clc.getResourceAsStream(name) : null;
        return in != null ? in :
                getClass().getClassLoader().getResourceAsStream(name);
    }

    public void define(String name, byte[] content) {
        defined.put(name, content);
    }

    boolean exists(String name) {
        if (parsed.containsKey(name))
            return true;
        Boolean known = (Boolean) existsCache.get(name);
        if (known != null)
            return known.booleanValue();
        String fn = name.concat(".class");
        boolean found = false;
        for (int i = 0; i < classPath.length; ++i)
            if (classPath[i].exists(fn)) {
                found = true;
                break;
            }
        ClassLoader clc;
        InputStream in;
        if (!found &&
              (clc = Thread.currentThread().getContextClassLoader()) != null &&
              (in = clc.getResourceAsStream(fn)) != null) {
            found = true;
            try {
                in.close();
            } catch (Exception ex) {
            }
        }
        existsCache.put(name, Boolean.valueOf(found));
        return found;
    }

    JavaTypeReader readClass(String className) {
        JavaTypeReader t = new JavaTypeReader();
        t.className = className;
        Object classNode = parsed.get(className);
        if (classNode != null) {
            JavaSource.loadClass(this, t, (JavaNode) classNode);
            return t;
        }
        InputStream in = findClass(className + ".class");
        if (in == null)
            return null;
        try {
            new ClassReader(in).accept(t, null,
                    ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
        } catch (IOException ex) {
            return null;
        }
        return t;
    }

    static ClassFinder get() {
        return CompileCtx.current().classPath;
    }
}
