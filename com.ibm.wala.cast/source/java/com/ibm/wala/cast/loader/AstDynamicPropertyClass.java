/******************************************************************************
 * Copyright (c) 2002 - 2006 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *****************************************************************************/
package com.ibm.wala.cast.loader;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.ibm.wala.cast.tree.CAstSourcePositionMap;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IClassLoader;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.Selector;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.types.annotations.Annotation;
import com.ibm.wala.util.strings.Atom;

public abstract class AstDynamicPropertyClass extends AstClass {
  private final TypeReference defaultDescriptor;

  protected AstDynamicPropertyClass(CAstSourcePositionMap.Position sourcePosition, TypeName typeName, IClassLoader loader,
      short modifiers, Map<Selector, IMethod> declaredMethods, TypeReference defaultDescriptor) {
    super(sourcePosition, typeName, loader, modifiers, new HashMap<Atom, IField>(), declaredMethods);
    this.defaultDescriptor = defaultDescriptor;
  }

  @Override
  public IField getField(final Atom name) {
    if (declaredFields.containsKey(name)) {
      return declaredFields.get(name);
    } else if (getSuperclass() != null) {
      return getSuperclass().getField(name);
    } else {
      final boolean isStatic = isStaticField(name);
      declaredFields.put(name, new IField() {
        @Override
        public String toString() {
          return "<field " + name + ">";
        }

        @Override
        public IClass getDeclaringClass() {
          return AstDynamicPropertyClass.this;
        }

        @Override
        public Atom getName() {
          return name;
        }

        @Override
        public TypeReference getFieldTypeReference() {
          return defaultDescriptor;
        }

        @Override
        public FieldReference getReference() {
          return FieldReference.findOrCreate(AstDynamicPropertyClass.this.getReference(), name, defaultDescriptor);
        }

        @Override
        public boolean isFinal() {
          return false;
        }

        @Override
        public boolean isPrivate() {
          return false;
        }

        @Override
        public boolean isProtected() {
          return false;
        }

        @Override
        public boolean isPublic() {
          return false;
        }

        @Override
        public boolean isVolatile() {
          return false;
        }

        @Override
        public boolean isStatic() {
          return isStatic;
        }

        @Override
        public IClassHierarchy getClassHierarchy() {
          return AstDynamicPropertyClass.this.getClassHierarchy();
        }
        
        @Override
        public Collection<Annotation> getAnnotations() {
          return Collections.emptySet();
        }
      });

      return declaredFields.get(name);
    }

  }

  protected boolean isStaticField(Atom name) {
    return name.toString().startsWith("global ");
  }

}
