<template>
  <div class="w100 h100">
    <div
      v-if="paragraphs == null"
      class="code-overlay-body"
    >
      <div
        class="code-loader"
      >
        <span>{</span>
        <span>}</span>
      </div>
    </div>

    <div
      v-else
      class="note">
      <div
        class="nb-controls-container"
      >
        <NoteControls :noteId="note.id" />
      </div>

      <div
        class="paragraph-container scrollbar"
      >
        <Paragraph
          :setActive="setActive"
          :class="{ active: activeParagraphId === paragraph.id }"
          :noteId="note.id"
          :index="index"
          :key="paragraph.id"
          v-for="(paragraph, index) in paragraphs"
          :paragraph="paragraph"
        />
      </div>
    </div>
  </div>
</template>

<script>
import wsFactory from '@/services/ws-factory'

import NoteControls from './Controls.vue'
import Paragraph from './paragraphs/Paragraph.vue'

export default {
  name: 'Note',
  props: {
    note: { required: true }
  },
  components: {
    NoteControls,
    Paragraph
  },
  data () {
    return {

    }
  },
  computed: {
    activeParagraphId () {
      let activeParagraph = this.$store.getters.getActiveParagraph

      if (!activeParagraph) {
        return
      }

      return activeParagraph.id
    },
    currentNote () {
      let filteredNote = this.$store.state.NotebookStore.notes.find(n => n.id === this.note.id)
      return filteredNote
    },
    paragraphs () {
      return this.currentNote && this.currentNote.paragraphs
    }
  },
  mounted () {
    this.fetchNote()
  },
  methods: {
    setActive: function (paragraph) {
      this.$store.dispatch('setActiveParagraph', paragraph)
    },
    fetchNote () {
      wsFactory.getConn(this.note.id).send({
        op: 'GET_NOTE',
        data: {
          id: this.note.id
        }
      })
    }
  }
}
</script>

<style scoped>
.note {
  position: relative;
  height: 100%;
}

.nb-controls-container {
  position: absolute;
  width: 100%;
}

.paragraph-container {
  padding: 15px;
  background: #FFF;
  box-shadow: 0 4px 5px 0 rgba(0, 0, 0, 0.14), 0 1px 10px 0 rgba(255, 255, 255, 0.12), 0 2px 4px -1px rgba(255, 255, 255, 0.4);
  height: 100%;

  height: calc(100% - 30px);
  overflow: auto;
  position: relative;
  top: 30px;
}
</style>
