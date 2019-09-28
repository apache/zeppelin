<template>
  <div class="md-editor-inner">
    <editor-menu-bar :editor="editor">
      <div class="menubar" slot-scope="{ commands, isActive }">

        <button
          class="menubar__button"
          :class="{ 'is-active': isActive.bold() }"
          @click="commands.bold"
        >
          <i data-feather="bold"></i>
        </button>

        <button
          class="menubar__button"
          :class="{ 'is-active': isActive.italic() }"
          @click="commands.italic"
        >
          <i data-feather="italic"></i>
        </button>

        <button
          class="menubar__button"
          :class="{ 'is-active': isActive.underline() }"
          @click="commands.underline"
        >
          <i data-feather="underline"></i>
        </button>

        <button
          class="menubar__button"
          :class="{ 'is-active': isActive.heading({ level: 1 }) }"
          @click="commands.heading({ level: 1 })"
        >
          H1
        </button>

        <button
          class="menubar__button"
          :class="{ 'is-active': isActive.heading({ level: 2 }) }"
          @click="commands.heading({ level: 2 })"
        >
          H2
        </button>

        <button
          class="menubar__button"
          :class="{ 'is-active': isActive.heading({ level: 3 }) }"
          @click="commands.heading({ level: 3 })"
        >
          H3
        </button>

        <button
          class="menubar__button"
          :class="{ 'is-active': isActive.bullet_list() }"
          @click="commands.bullet_list"
        >
          <i data-feather="list"></i>
        </button>

        <button
          class="menubar__button"
          @click="commands.undo"
        >
          <i data-feather="corner-up-left"></i>
        </button>

        <button
          class="menubar__button"
          @click="commands.redo"
        >
          <i data-feather="corner-up-right"></i>
        </button>

      </div>
    </editor-menu-bar>

    <editor-content class="editor__content" :editor="editor" />
  </div>
</template>

<script>
import { Editor, EditorContent, EditorMenuBar } from 'tiptap'
import {
  Blockquote,
  CodeBlock,
  HardBreak,
  Heading,
  HorizontalRule,
  OrderedList,
  BulletList,
  ListItem,
  TodoItem,
  TodoList,
  Bold,
  Code,
  Italic,
  Link,
  Strike,
  Underline,
  History
} from 'tiptap-extensions'

export default {
  components: {
    EditorContent,
    EditorMenuBar
  },
  props: ['content', 'onMdUpdate', 'setMDParagraph', 'forceEditorShow', 'noteId'],
  data () {
    return {
      editor: null
    }
  },
  methods: {
    // setMDParagraph: function () {
    //   let {id} = this.$props.paragraph
    //   let paragraph = this.$store.getters.getParagraphById(id, this.$props.noteId)

    //   const {id} = this.$props.paragraph
    //   const paragraph = this.$store.getters.getParagraphById(id, this.$props.noteId)

    //   if (paragraph && paragraph.forceEditorShow) {
    //     return true
    //   }
    // }
  },
  mounted () {
    let _this = this

    this.editor = new Editor({
      extensions: [
        new Blockquote(),
        new BulletList(),
        new CodeBlock(),
        new HardBreak(),
        new Heading({ levels: [1, 2, 3] }),
        new HorizontalRule(),
        new ListItem(),
        new OrderedList(),
        new TodoItem(),
        new TodoList(),
        new Bold(),
        new Code(),
        new Italic(),
        new Link(),
        new Strike(),
        new Underline(),
        new History()
      ],
      content: this.$props.content,
      onUpdate: function (cb) {
        _this.$props.onMdUpdate(cb.getHTML())
      }
    })

    document.addEventListener('mousedown', event => {
      let target = event.target
      if (
        target.parentElement &&
        (target.parentElement.className ===
          'md-editor-inner' ||
          target.parentElement.className === 'menubar' ||
          target.parentElement.className === 'menubar__button' ||
          target.parentElement.className === 'ProseMirror' ||
          target.parentElement.className === 'ProseMirror ProseMirror-focused' ||
          target.parentElement.className === 'editor__content')
      ) {
        // if clicked inside do nothing
      } else {
        // clicked outside the md editor
        // save the content
        if (this.$props.forceEditorShow) {
          this.$props.setMDParagraph()
        }
      }
    })
  },
  beforeDestroy () {
    this.editor.destroy()
  }
}
</script>

<style lang="scss" scoped>
.md-editor-inner {
  .ProseMirror {
    outline: 0;
    padding: 10px;
  }

  .menubar {
    padding: 10px;
    background: #f7f7f7;

    button {
      background: transparent;
      border: 0;
      outline: 0;

      &:focus,
      &:active,
      &:hover {
        outline: 0;
        background: #eaeaea;
        border-radius: 3px;
      }

      svg {
        font-size: 12px;
        width: 18px;
      }
    }
  }

  ul li p {
    margin: 0;
  }
}
</style>
