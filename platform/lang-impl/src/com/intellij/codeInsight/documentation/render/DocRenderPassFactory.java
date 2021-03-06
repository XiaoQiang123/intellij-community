// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.documentation.render;

import com.intellij.codeHighlighting.*;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.documentation.DocumentationComponent;
import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocCommentBase;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiModificationTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DocRenderPassFactory implements TextEditorHighlightingPassFactoryRegistrar, TextEditorHighlightingPassFactory {
  private static final Logger LOG = Logger.getInstance(DocRenderPassFactory.class);
  private static final Key<Long> MODIFICATION_STAMP = Key.create("doc.render.modification.stamp");
  private static final Key<Boolean> ICONS_ENABLED = Key.create("doc.render.icons.enabled");
  private static final Pattern END_OF_BODY_PATTERN = Pattern.compile("</body>|</html>");
  private static final char ZERO_WIDTH_SPACE = '\u200b';

  @Override
  public void registerHighlightingPassFactory(@NotNull TextEditorHighlightingPassRegistrar registrar, @NotNull Project project) {
    registrar.registerTextEditorHighlightingPass(this, TextEditorHighlightingPassRegistrar.Anchor.AFTER, Pass.UPDATE_FOLDING, false, false);
  }

  @Nullable
  @Override
  public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull Editor editor) {
    long current = PsiModificationTracker.SERVICE.getInstance(file.getProject()).getModificationCount();
    boolean iconsEnabled = DocRenderDummyLineMarkerProvider.isGutterIconEnabled();
    Long existing = editor.getUserData(MODIFICATION_STAMP);
    Boolean iconsWereEnabled = editor.getUserData(ICONS_ENABLED);
    return editor.getProject() == null ||
           existing != null && existing == current && iconsWereEnabled != null && iconsWereEnabled == iconsEnabled
           ? null : new DocRenderPass(editor, file);
  }

  static void forceRefreshOnNextPass(@NotNull Editor editor) {
    editor.putUserData(MODIFICATION_STAMP, null);
  }

  private static class DocRenderPass extends EditorBoundHighlightingPass {
    private Items items;

    DocRenderPass(@NotNull Editor editor, @NotNull PsiFile psiFile) {
      super(editor, psiFile, false);
    }

    @Override
    public void doCollectInformation(@NotNull ProgressIndicator progress) {
      items = calculateItemsToRender(Objects.requireNonNull(myDocument), myFile);
    }

    @Override
    public void doApplyInformationToEditor() {
      applyItemsToRender(myEditor, myProject, items, false);
    }
  }

  @NotNull
  public static Items calculateItemsToRender(@NotNull Document document, @NotNull PsiFile psiFile) {
    boolean enabled = EditorSettingsExternalizable.getInstance().isDocCommentRenderingEnabled();
    Items items = new Items();
    DocumentationManager.getProviderFromElement(psiFile).collectDocComments(psiFile, comment -> {
      TextRange range = comment.getTextRange();
      if (range != null && DocRenderItem.isValidRange(document, range)) {
        String textToRender = enabled ? calcText(comment) : null;
        items.addItem(new Item(range, textToRender));
      }
    });
    return items;
  }

  static @NotNull String calcText(@Nullable PsiDocCommentBase comment) {
    try {
      String text = null;
      if (comment != null) {
        PsiElement owner = comment.getOwner();
        if (owner != null) {
          text = DocumentationManager.getProviderFromElement(owner).generateRenderedDoc(owner);
        }
      }
      return text == null ? CodeInsightBundle.message("doc.render.not.available.text") : preProcess(text);
    }
    catch (IndexNotReadyException e) {
      LOG.warn(e);
      return CodeInsightBundle.message("doc.render.dumb.mode.text");
    }
  }

  private static String preProcess(String text) {
    String result = DocumentationComponent.addExternalLinksIcon(text);
    // add zero width space to document's body, this makes JEditorPane wrap text not just at spaces, but e.g. on dots
    Matcher matcher = END_OF_BODY_PATTERN.matcher(result);
    if (matcher.find()) {
      int endOfBody = matcher.start();
      return result.substring(0, endOfBody) + ZERO_WIDTH_SPACE + result.substring(endOfBody);
    }
    else {
      return result + ZERO_WIDTH_SPACE;
    }
  }

  public static void applyItemsToRender(@NotNull Editor editor,
                                        @NotNull Project project,
                                        @NotNull Items items,
                                        boolean collapseNewRegions) {
    editor.putUserData(MODIFICATION_STAMP, PsiModificationTracker.SERVICE.getInstance(project).getModificationCount());
    editor.putUserData(ICONS_ENABLED, DocRenderDummyLineMarkerProvider.isGutterIconEnabled());
    DocRenderItem.setItemsToEditor(editor, items, collapseNewRegions);
  }

  public static class Items implements Iterable<Item> {
    private final Map<TextRange, Item> myItems = new LinkedHashMap<>();

    boolean isEmpty() {
      return myItems.isEmpty();
    }

    private void addItem(@NotNull Item item) {
      myItems.put(item.textRange, item);
    }

    @Nullable
    Item removeItem(@NotNull Segment textRange) {
      return myItems.remove(TextRange.create(textRange));
    }

    @NotNull
    @Override
    public Iterator<Item> iterator() {
      return myItems.values().iterator();
    }
  }

  static class Item {
    final TextRange textRange;
    final String textToRender;

    private Item(@NotNull TextRange textRange, @Nullable String textToRender) {
      this.textRange = textRange;
      this.textToRender = textToRender;
    }
  }
}
