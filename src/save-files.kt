package burp

import org.apache.commons.fileupload.DefaultFileItemFactory
import org.apache.commons.fileupload.FileItem
import org.apache.commons.fileupload.FileUpload
import org.apache.commons.fileupload.RequestContext
import java.awt.Frame
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Paths
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JMenuItem
import javax.swing.JOptionPane


class ContextMenuFactory : IContextMenuFactory {
    override fun createMenuItems(invocation: IContextMenuInvocation): MutableList<JMenuItem> {
        val fileUpload = FileUpload(DefaultFileItemFactory())
        var allFiles = arrayListOf<FileItem>()
        for(message in invocation.selectedMessages) {
            val requestContext = BytesRequestContext(message.request!!)
            if(FileUpload.isMultipartContent(requestContext)) {
                for(file in fileUpload.parseRequest(requestContext)) {
                    if(file.name != null) {
                        allFiles.add(file)
                    }
                }
            }
        }
        val menuItem = JMenuItem("Save uploaded files")
        menuItem.addActionListener(SaveUploadedFiles(allFiles))
        return if(allFiles.size > 0) arrayListOf(menuItem) else arrayListOf()
    }
}


class SaveUploadedFiles(val files: List<FileItem>) : ActionListener {
    override fun actionPerformed(e: ActionEvent?) {
        try {
            val fileChooser = JFileChooser()
            fileChooser.dialogTitle = "Select directory to save files into"
            fileChooser.approveButtonText = "Save ${files.size} files"
            fileChooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            fileChooser.isAcceptAllFileFilterUsed = false
            if (fileChooser.showOpenDialog(getBurpFrame()) != JFileChooser.APPROVE_OPTION) {
                return
            }
            for (file in files) {
                val safeName = Paths.get(file.name).fileName // avoid path injection
                val path = fileChooser.selectedFile.toPath().resolve(safeName)
                if (Files.exists(path)) {
                    val result = JOptionPane.showConfirmDialog(getBurpFrame(), "A file named '${safeName}' already exists. Do you want to overwrite it?", "Save Files", JOptionPane.YES_NO_OPTION)
                    if (result != JOptionPane.YES_OPTION) {
                        continue
                    }
                }
                Files.write(path, file.get())
            }
        }
        catch(ex: Exception) {
            BurpExtender.callbacks.printError(ex.toString())
            ex.printStackTrace(PrintWriter(BurpExtender.callbacks.stderr))
        }
    }
}


class BurpExtender : IBurpExtender {
    companion object {
        lateinit var callbacks: IBurpExtenderCallbacks
    }

    override fun registerExtenderCallbacks(cb: IBurpExtenderCallbacks) {
        callbacks = cb
        callbacks.setExtensionName("Save uploaded files")
        callbacks.registerContextMenuFactory(ContextMenuFactory())
    }
}


class BytesRequestContext(val request: ByteArray) : RequestContext {
    override fun getContentLength(): Int {
        return request.size
    }

    override fun getContentType(): String {
        val requestInfo = BurpExtender.callbacks.helpers.analyzeRequest(request)
        for(header in requestInfo.headers) {
            if(header.startsWith("Content-Type: ")) {
                return header.substringAfter("Content-Type: ")
            }
        }
        return ""
    }

    override fun getInputStream(): InputStream {
        return ByteArrayInputStream(request)
    }

    override fun getCharacterEncoding(): String {
        return "utf-8"
    }
}


fun getBurpFrame(): JFrame? {
    for (f in Frame.getFrames()) {
        if (f.isVisible && f.title.startsWith("Burp Suite")) {
            return f as JFrame
        }
    }
    return null
}
