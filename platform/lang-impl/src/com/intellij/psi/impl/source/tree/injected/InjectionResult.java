// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree.injected;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.injection.ReferenceInjector;

import java.util.List;

class InjectionResult {
  final List<? extends PsiFile> files;
  final List<? extends Pair<ReferenceInjector, Place>> references;
  private final long myModificationCount;

  InjectionResult(List<? extends PsiFile> files, List<? extends Pair<ReferenceInjector, Place>> references) {
    this.files = files;
    this.references = references;
    myModificationCount = calcModCount();
    if (files == null && references == null) throw new IllegalArgumentException("At least one argument must not be null");
  }

  boolean isValid() {
    if (files != null) {
      for (PsiFile file : files) {
        if (!file.isValid()) return false;
      }
    }
    else {
      for (Pair<ReferenceInjector, Place> pair : references) {
        Place place = pair.getSecond();
        if (!place.isValid()) return false;
      }
    }
    return true;
  }

  boolean isModCountUpToDate() {
    return myModificationCount == calcModCount();
  }

  private long calcModCount() {
    long modCount = 0;
    if (files != null) {
      for (PsiFile file : files) {
        if (!file.isValid()) return -1;
        modCount += file.getModificationStamp();
        modCount += file.getManager().getModificationTracker().getModificationCount();
      }
    }
    if (references != null) {
      for (Pair<ReferenceInjector, Place> pair : references) {
        Place place = pair.getSecond();
        if (!place.isValid()) return -1;
        for (PsiLanguageInjectionHost.Shred shred : place) {
          PsiLanguageInjectionHost host = shred.getHost();
          if (host == null || !host.isValid()) return -1;
          PsiFile file = host.getContainingFile();
          modCount += file.getModificationStamp();
          modCount += file.getManager().getModificationTracker().getModificationCount();
        }
      }
    }
    return modCount;
  }
}
