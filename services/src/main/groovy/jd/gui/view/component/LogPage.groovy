/*
 * Copyright (c) 2008-2015 Emmanuel Dupuy
 * This program is made available under the terms of the GPLv3 License.
 */

package jd.gui.view.component

import jd.gui.api.API
import jd.gui.api.feature.ContentSavable
import jd.gui.api.feature.IndexesChangeListener
import jd.gui.api.feature.UriGettable
import jd.gui.api.model.Indexes

import java.awt.Point

class LogPage extends HyperlinkPage implements UriGettable, ContentSavable, IndexesChangeListener {
    protected API api
    protected URI uri
    protected Collection<Indexes> collectionOfIndexes

    LogPage(API api, URI uri, String content) {
        this.api = api
        this.uri = uri
        // Parse
        int index = 0
        int eol = content.indexOf('\n')

        while (eol != -1) {
            parseLine(content, index, eol)
            index = eol + 1
            eol = content.indexOf('\n', index)
        }

        parseLine(content, index, content.size())
        // Display
        setText(content)
    }

    protected void parseLine(String content, int index, int eol) {
        int start = content.indexOf('at ', index)

        if ((start != -1) && (start < eol)) {
            int leftParenthesisIndex = content.indexOf('(', start)

            if ((leftParenthesisIndex != -1) && (leftParenthesisIndex < eol)) {
                addHyperlink(new LogHyperlinkData(start+3, leftParenthesisIndex))
            }
        }
    }

    protected boolean isHyperlinkEnabled(HyperlinkData hyperlinkData) { hyperlinkData.enabled }

    protected void openHyperlink(int x, int y, HyperlinkData hyperlinkData) {
        if (hyperlinkData.enabled) {
            // Save current position in history
            def location = textArea.getLocationOnScreen()
            int offset = textArea.viewToModel(new Point(x-location.x as int, y-location.y as int))
            api.addURI(new URI(uri.scheme, uri.authority, uri.path, 'position=' + offset, null))

            // Open link
            def text = getText()
            def typeAndMethodNames = text.substring(hyperlinkData.startPosition, hyperlinkData.endPosition)
            int lastDotIndex = typeAndMethodNames.lastIndexOf('.')
            def methodName = typeAndMethodNames.substring(lastDotIndex+1)
            def typeName = typeAndMethodNames.substring(0, lastDotIndex).replace('.', '/')
            def entries = collectionOfIndexes?.collect { it.getIndex('typeDeclarations')?.get(typeName) }.flatten().grep { it!=null }

            int leftParenthesisIndex = hyperlinkData.endPosition + 1
            int rightParenthesisIndex = text.indexOf(')', leftParenthesisIndex)
            def lineNumberOrNativeMethodFlag = text.substring(leftParenthesisIndex, rightParenthesisIndex)

            if (lineNumberOrNativeMethodFlag.equals('Native Method')) {
                // Example: at java.security.AccessController.doPrivileged(Native Method)
                lastDotIndex = typeName.lastIndexOf('/')
                def shortTypeName = typeName.substring(lastDotIndex+1)
                api.openURI(x, y, entries, null, shortTypeName + '-' + methodName + '-(?)?')
            } else {
                // Example: at sun.misc.Launcher$AppClassLoader.loadClass(Launcher.java:294)
                int colonIndex = lineNumberOrNativeMethodFlag.indexOf(':')
                def lineNumber = lineNumberOrNativeMethodFlag.substring(colonIndex+1)
                api.openURI(x, y, entries, 'lineNumber=' + lineNumber, null)
            }
        }
    }

    // --- UriGettable --- //
    URI getUri() { uri }

    // --- SourceSavable --- //
    String getFileName() {
        def path = uri.path
        int index = path.lastIndexOf('/')
        return path.substring(index + 1)
    }

    void save(API api, OutputStream os) {
        os << textArea.text
    }

    // --- IndexesChangeListener --- //
    void indexesChanged(Collection<Indexes> collectionOfIndexes) {
        // Update the list of containers
        this.collectionOfIndexes = collectionOfIndexes
        // Refresh links
        boolean refresh = false
        def text = getText()

        for (def entry : hyperlinks.entrySet()) {
            def entryData = entry.value as LogHyperlinkData
            def typeAndMethodNames = text.substring(entryData.startPosition, entryData.endPosition)
            int lastDotIndex = typeAndMethodNames.lastIndexOf('.')
            def typeName = typeAndMethodNames.substring(0, lastDotIndex).replace('.', '/')
            boolean enabled = collectionOfIndexes.find { it.getIndex('typeDeclarations')?.get(typeName) } != null

            if (entryData.enabled != enabled) {
                entryData.enabled = enabled
                refresh = true
            }
        }

        if (refresh) {
            textArea.repaint()
        }
    }

    static class LogHyperlinkData extends HyperlinkPage.HyperlinkData {
        boolean enabled = false

        LogHyperlinkData(int startPosition, int endPosition) {
            super(startPosition, endPosition)
        }
    }
}
