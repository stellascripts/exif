EXIF
====
**Easy Xml Interactive Fiction**

EXIF is a small project made for the purposes of laying out simple interactive fiction as
part of XML files, to be displayed in the browser.

EXIF Setup
----------
To operate properly in an HTML document:
 * The scripts 'kotlin.js' and 'exif.js' must be loaded, in that order.
 * There must exist elements with classes:
   * exif-title
   * exif-desc
   * exif-speaker
   * exif-text
   * exif-buttons

EXIF Tags
---------
    <block if-set='*' if-clear='*' trip='*'>
The block is the main execution element of an EXIF XML file. All tags within the block
are executed in sequence. Blocks which occur inside other blocks are executed, if:
 * All of the flags in the attribute `if-set` are set.
 * All of the flags in the attribute `if-clear` are clear.
 * All of the flags in the attribute `trip` are clear.

After entering the inner block, all the flags in the `trip` attribute are set as if by the tag
`<flag set>`. After executing the inner block, execution then returns to the outer block,
unless no outer block exists, in which case `END` is written to the element with class `exif-buttons`.

    <title>
Writes the enclosed text to the element with class `exif-title`.

    <description>
Writes the enclosed text to the element with class `exif-desc`.
    
    <text speaker='*'>
Writes the enclosed text to the element with class `exif-text`, then pauses
execution and presents a Next button to the element with class `exif-buttons`,
as if by the `<pause/>` tag.

If the `speaker` attribute is set, that attribute's contents are also written
to the element with class `exif-speaker`.

    <[^a-z].*>
Tags starting with a non-lowercase letter are interpreted as though they are
`<text>` blocks with the `speaker` attribute set to the tag name, separated with
spaces before each capital letter after the first.

For example, `<JohnSmith>` becomes `<text speaker='John Smith'>`.
    
    <to-description>
All of the `<text>` tags inside this tag will be appended to the description
individually after the user clicks Next.
    
    <pause/>
Pauses execution and writes a Next button to the element with class `exif-buttons`.
    
    <choice>
        <option of='*' if-set='*' if-clear='*' trip='*'>
Presents a series of choices. Each of the option tags within the `<choice>` block will be
presented to the user as links (`<a>` elements) containing the contents of the `of` attribute.
Once the user selects a choice, execution resumes as if the option selected were a `<block>`.

Since options are executed as blocks, any option with attributes `if-set`, `if-clear`, `trip`
which would prevent block execution is not presented to the user.
        
    <flag set='*' clear='*'>
Sets/clears flags. Flag names may be arbitrary, as they are only referenced in EXIF XML
files. Multiple flags are separated by a space.
    
    <call block='*'>
Sets up execution at the block with the id specified in the `block` attribute.
The block must be contained in the currently loaded file. After executing the called block,
execution resumes directly after the `<call>` tag.
    
    <goto block='*'>
Sets up execution at the block with the id specified in the `block` attribute.
The block must be contained in the currently loaded file. After executing the referenced block,
execution does **not** resume after the `<goto>` tag: the previous execution stack is cleared by
this instruction.
    
    <load file='*' block='*'>
Fetches a new file and starts execution at the block with the specified id, or the first block if
no `block` attribute is specified. The previous execution stack is cleared, and the current file will
not be returned to after execution.