import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import org.w3c.dom.get
import org.w3c.xhr.XMLHttpRequest

/**
 * Annotation indicating exported functions.
 */
annotation class Out

internal fun <T> ArrayList<T>.push(t: T) {
    this.add(t)
}

internal fun <T> ArrayList<T>.pop(): T {
    return this.removeAt(this.size - 1)
}

internal fun <T> ArrayList<T>.peek(): T {
    return this[this.size - 1]
}

internal class StackFrame(nodeList: NodeList, val type: Type, var index: Int = 0) {
    val elements = ArrayList<Element>()
    init {
        for(i in 0 until nodeList.length) {
            val e = (nodeList.item(i) as? Element)?:continue
            elements.add(e)
        }
    }

    fun nextItem() = elements[index++]
    val exceeded get() = index >= elements.size

    enum class Type {
        Standard,
        Option,
        ToDescription
    }
}

internal fun Element.attr(name: String): String? {
    return attributes.getNamedItem(name)?.value
}

internal fun Element.attrs(vararg names: String): List<String?> {
    return names.map { attributes.getNamedItem(it)?.value }
}

internal fun caseToSpaces(string: String?): String? {
    if(string == null) return null
    var result = ""
    var idx = 0
    for(i in idx until string.length) {
        if(string[i] == string[i].toUpperCase()) {
            if(i == 0) continue
            result += string.substring(idx, i)
            result += " "
            idx = i
        }
    }
    result += string.substring(idx, string.length)
    return result
}

internal const val DEFAULT_TARGET = "DEFAULT"
internal const val SPEAKER_TARGET = "SPEAKER"
internal const val INTERACTION_TARGET = "INTERACTION"
internal const val TITLE_TARGET = "TITLE"
internal const val DESCRIPTION_TARGET = "DESCRIPTION"
internal val FLAGS = HashSet<String>()

/**
 * Sets EXIF flags, separated by spaces.
 */
fun setFlags(flags: String): Boolean {
    return FLAGS.addAll(flags.split(' '))
}

/**
 * Clears EXIF flags, separated by spaces.
 */
fun clearFlags(flags: String): Boolean {DEFAULT_TARGET
    return FLAGS.removeAll(flags.split(' '))
}

/**
 * Checks EXIF flags, separated by spaces.
 */
fun checkFlags(flags: String): Boolean {
    return FLAGS.containsAll(flags.split(' '))
}


internal fun exifFetch(file: String): Document {
    val req = XMLHttpRequest()
    req.open("GET", file, false)
    req.overrideMimeType("text/xml")
    req.send()
    if(req.status != 200.toShort()) error(req.statusText)
    return req.responseXML!!
}

/**
 * The interpreter being referenced for EXIF-specific interactions.
 */
lateinit var CURRENT_INTERPRETER: Interpreter

internal fun findClass(cls: String) = kotlin.browser.document.getElementsByClassName(cls)[0]?:
        error("No element of class $cls found.")

/**
 * Main EXIF interpreter class. Loads and plays EXIF XML scripts.
 */
class Interpreter {
    private val defaultTarget: Element
    private val speakerTarget: Element
    private val interactionTarget: Element
    private val titleTarget: Element
    private val descriptionTarget: Element

    init {
        CURRENT_INTERPRETER = this
        defaultTarget = findClass("exif-text")
        speakerTarget = findClass("exif-speaker")
        interactionTarget = findClass("exif-buttons")
        titleTarget = findClass("exif-title")
        descriptionTarget = findClass("exif-desc")
    }

    private val stack = ArrayList<StackFrame>()
    private lateinit var document: Document
    private val blockIDs = LinkedHashMap<String, Element>()

    private var isPaused = false
    private val options = HashMap<String, Element>()
    private var appendToDesc: String? = null

    /**
     * Loads a new file and starts it at the indicated block.
     */
    @JsName("load")
    @Out
    fun load(file: String, block: String? = null) {
        loadDocument(exifFetch(file))
        if(block != null) call(block)
        else call(blockIDs.entries.first().key)
    }

    private fun loadDocument(doc: Document) {
        document = doc
        stack.clear()

        val blocks = doc.getElementsByTagName("block")
        for(i in 0 until blocks.length) {
            val block = blocks.item(i)?:continue
            val id = block.attr("id")
            if(id != null) {
                blockIDs[id] = block
            }
        }
    }

    /**
     * Starts execution of the current EXIF block.
     */
    @JsName("play")
    @Out
    fun play() {
        while(step() && !isPaused) {}
    }

    /**
     * Goes through one step of the current EXIF block.
     */
    @JsName("step")
    @Out
    fun step(): Boolean {
        val frame = stack.peek()
        val ele = frame.nextItem()
        interpret(ele)
        while(stack.peek().exceeded) {
            stack.pop()
        }
        return stack.isNotEmpty()
    }

    private fun call(id: String) {
        val block = blockIDs[id]?:error("Unknown block: <$id>")
        stack.push(StackFrame(block.childNodes, StackFrame.Type.Standard))
    }

    private fun target(target: String) = when(target) {
        TITLE_TARGET -> titleTarget
        DESCRIPTION_TARGET -> descriptionTarget
        DEFAULT_TARGET -> defaultTarget
        INTERACTION_TARGET -> interactionTarget
        SPEAKER_TARGET -> speakerTarget
        else -> error("Element not found: <$target>")
    }

    private fun writeTo(target: String, text: String) {
        val ele = target(target)
        ele.innerHTML = text
    }

    private fun readFrom(target: String): String {
        val ele = target(target)
        return ele.innerHTML
    }

    private fun pause() {
        writeTo(INTERACTION_TARGET, "<a href='javascript:exif.CURRENT_INTERPRETER.resume()'>Next</a>")
        isPaused = true
    }

    /**
     * Resumes execution of the current EXIF block after making a selection.
     */
    @JsName("resume")
    @Out
    fun resume() {
        isPaused = false
        if(appendToDesc != null) {
            writeTo(DESCRIPTION_TARGET, readFrom(DESCRIPTION_TARGET) + " " + appendToDesc)
            appendToDesc = null
        }
        play()
    }

    /**
     * Selects an option from a <choice> EXIF instruction and resumes execution.
     */
    @JsName("choose")
    @Out
    fun choose(label: String) {
        val option = options[label]
        if(option != null) {
            stack.push(StackFrame(option.childNodes, StackFrame.Type.Option))
            options.clear()
            resume()
        } else {
            error("Bad option: <$label>")
        }
    }

    private fun lookahead(): Element? {
        var stackIdx = stack.size-1
        println("Looking Ahead")
        while (true) {
            println("Stack Frame: $stackIdx")
            if(stackIdx < 0) {
                println("Reached Bottom of Stack")
                return null
            }
            val frame = stack[stackIdx--]
            println("Frame Exceeded? ${if(frame.exceeded) "Yes" else "No"}")
            if (!frame.exceeded) {
                println("Returning ${frame.elements[frame.index].tagName}")
                return frame.elements[frame.index]
            }
        }
    }

    private fun text(ele: Element, speaker: String?) {
        if(speaker != null) {
            writeTo(SPEAKER_TARGET, speaker)
        } else {
            writeTo(SPEAKER_TARGET, "&ZeroWidthSpace;")
        }
        val text = ele.innerHTML.trim()
        writeTo(DEFAULT_TARGET, if(speaker == null) text else "\"$text\"" )

        if(stack.any { it.type == StackFrame.Type.ToDescription }) appendToDesc = ele.innerHTML

        val nextEle = lookahead()
        if(nextEle == null) {
            writeTo(INTERACTION_TARGET, "END")
        } else if(nextEle.tagName != "choice") {
            pause()
        }
    }

    private fun interpret(ele: Element) {
        when(ele.tagName) {
            /*
            The block is the main execution element of an EXIF XML file. All tags within the block
            are executed in sequence. Blocks which occur inside other blocks are executed, if:
             * All of the flags in the attribute `if-set` are set.
             * All of the flags in the attribute `if-clear` are clear.
             * All of the flags in the attribute `trip` are clear.

            After entering the inner block, all the flags in the `trip` attribute are set as if by the tag
            `<flag set>`. After executing the inner block, execution then returns to the outer block,
            unless no outer block exists, in which case `END` is written to the element with class `exif-buttons`.
             */
            "block" -> {
                val (set, clear, trip) = ele.attrs("if-set", "if-clear", "trip")
                if(set != null && !checkFlags(set)) return
                if(clear != null && checkFlags(clear)) return
                if(trip != null) {
                    if(checkFlags(trip)) return
                    setFlags(trip)
                }
                stack.push(StackFrame(ele.childNodes, StackFrame.Type.Standard))
            }
            /*
            Writes the enclosed text to the element with class `exif-title`.
             */
            "title" -> writeTo(TITLE_TARGET, ele.innerHTML)
            /*
            Writes the enclosed text to the element with class `exif-desc`.
             */
            "description" -> writeTo(DESCRIPTION_TARGET, ele.innerHTML)
            /*
            Writes the enclosed text to the element with class `exif-text`, then pauses
            execution and presents a Next button to the element with class `exif-buttons`,
            as if by the `<pause/>` tag.

            If the `speaker` attribute is set, that attribute's contents are also written
            to the element with class `exif-speaker`.
             */
            "text" -> {
                val speaker = ele.attr("speaker")
                text(ele, speaker)
            }
            /*
            All of the `<text>` tags inside this tag will be appended to the description
            individually after the user clicks Next.
             */
            "to-description"->{
                stack.push(StackFrame(ele.childNodes, StackFrame.Type.ToDescription))
            }
            /*
            Clears any text present in all EXIF element classes.
             */
            "clear-screen" -> {
                writeTo(TITLE_TARGET, "")
                writeTo(DESCRIPTION_TARGET, "")
                writeTo(SPEAKER_TARGET, "")
                writeTo(DEFAULT_TARGET, "")
                writeTo(INTERACTION_TARGET, "")
            }
            /*
            Pauses execution and writes a Next button to the element with class `exif-buttons`.
             */
            "pause" -> pause()
            /*
            Presents a series of choices. Each of the option tags within the choice block will be
            presented to the user as links (`<a>` elements) with the contents of the `of` attribute.
            Once the user selects a choice, execution resumes as if the option selected were a `<block>`.

            Since options are executed as blocks, any option with attributes which would prevent block
            execution is not presented to the user.
             */
            "choice" -> {
                var choices = ""
                var unlinked = ""
                for(i in 0 until ele.childNodes.length) {
                    val optionEle = ele.childNodes[i] as? Element ?:continue
                    val (name, set, clear, trip) = optionEle.attrs("of", "if-set", "if-clear", "trip")
                    if(name == null) error("<option> tag must have 'of' attribute")
                    if(set != null && !checkFlags(set)) continue
                    if(clear != null && !checkFlags(clear)) continue
                    if(trip != null && !setFlags(trip)) continue
                    choices += "<a href='javascript:exif.CURRENT_INTERPRETER.choose(\"$name\")'>$name</a><br/>"
                    unlinked += "<a href='javascript:void()'>$name</a><br/>"
                    options[name] = optionEle
                }
                pause()
                writeTo(INTERACTION_TARGET, unlinked)
                kotlin.browser.window.setTimeout({
                    writeTo(INTERACTION_TARGET, choices)
                },300)
            }
            /*
            Sets/clears flags. Flag names may be arbitrary, as they are only referenced in EXIF XML
            files. Multiple flags are separated by a space.
             */
            "flag" -> {
                val (set, clear) = ele.attrs("set", "clear")
                if(set != null) setFlags(set)
                if(clear != null) clearFlags(clear)
            }
            /*
            Sets up execution at the block with the id specified in the `block` attribute.
            The block must be contained in the currently loaded file. After executing the called block,
            execution resumes directly after the `<call>` tag.
             */
            "call" -> {
                val target = ele.attr("block")
                call(target!!)
            }
            /*
            Sets up execution at the block with the id specified in the `block` attribute.
            The block must be contained in the currently loaded file. After executing the referenced block,
            execution does **not** resume after the `<goto>` tag: the previous execution stack is cleared by
            this instruction.
             */
            "goto" -> {
                val target = ele.attr("block")
                stack.clear()
                call(target!!)
            }
            /*
            Fetches a new file and starts execution at the block with the specified id, or the first block if
            no `block` attribute is specified. The previous execution stack is cleared, and the current file will
            not be returned to after execution.
             */
            "load" -> {
                val (file, block) = ele.attrs("file", "block")
                load(file!!, block)
            }
            /*
            Tags starting with a non-lowercase letter are interpreted as though they are
            `<text>` blocks with the `speaker` attribute set to the tag name, separated with
            spaces before each capital letter after the first.
             */
            else -> {
                val firstLetter = ele.tagName[0]
                if(firstLetter !in 'a'..'z') {
                    text(ele, caseToSpaces(ele.tagName))
                }
            }
        }
    }
}