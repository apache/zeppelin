<template>
  <div>
    <div id="menu-bar">
      <ul class="main-menu">
        <!-- File Menu -->
        <li> File
          <ul>
            <li>
              <a
                @click="executeNoteCommand('show-create')"
                href="javascript:void(0)"
              >
                New Note <span>Ctrl+N</span>
              </a>
            </li>
            <li>
              <a
                @click="executeNoteCommand('show-import')"
                href="javascript:void(0)"
              >
                Import
              </a>
            </li>

            <li class="separator"></li>

            <li>
              <a
                v-bind:class="{'disabled': !(isActiveNote)}"
                @click="executeNoteCommand('save')"
                href="javascript:void(0)"
              >
                Save <!--<span>Ctrl+S</span> -->
              </a>
            </li>

            <li>
              <a
                v-bind:class="{'disabled': !(isActiveNote)}"
                @click="executeNoteCommand('manage-permissions')"
                href="javascript:void(0)"
              >
                Manage Permissions
              </a>
            </li>

            <li class="separator"></li>
            <li>
              <a
                v-bind:class="{'disabled': !(isActiveNote)}"
                @click="executeNoteCommand('export-json')"
                href="javascript:void(0)"
              >
                Explort
              </a>
            </li>
            <li>
              <a
                v-bind:class="{'disabled': !(isActiveNote)}"
                @click="executeNoteCommand('delete-temporary')"
                href="javascript:void(0)"
              >
                Move To Recycle Bin
              </a>
            </li>

            <li class="separator"></li>

            <li>
              <a
                v-bind:class="{'disabled': !(isActiveNote)}"
                @click="executeNoteCommand('print')"
                href="javascript:void(0)"
              >
                Print
              </a>
            </li>
          </ul>
        </li>

        <!-- Edit Menu -->
        <li> Edit
          <ul>
            <li>
              <a
                @click="executeNoteCommand('toggle-code')"
                v-bind:class="{'disabled': !(isActiveNote)}"
                href="javascript:void(0)"
              >
                Show/Hide Code
              </a>
            </li>
            <li>
              <a
                @click="executeNoteCommand('toggle-line-numbers')"
                v-bind:class="{'disabled': !(isActiveNote)}"
                href="javascript:void(0)"
              >
                Show/Hide Line Numbers
              </a>
            </li>
            <li>
              <a
                @click="executeNoteCommand('toggle-output')"
                v-bind:class="{'disabled': !(isActiveNote)}"
                href="javascript:void(0)"
              >
                Show/Hide Outputs
              </a>
            </li>
            <li class="separator"></li>
            <li>
              <a
                v-bind:class="{'disabled': !(isActiveNote)}"
                @click="executeNoteCommand('find-and-replace')"
                href="javascript:void(0)"
              >
                Find And Replace...
              </a>
            </li>
            <li class="separator"></li>
            <li>
              <a
                @click="showConfirmClearOutput"
                v-bind:class="{'disabled': !(isActiveNote)}"
                href="javascript:void(0)"
              >
                Clear All Outputs
              </a>
            </li>
          </ul>
        </li>

        <!-- View Menu -->
        <li> View
          <ul>
            <li>
              <a
                v-bind:class="{'disabled': !(isActiveNote)}"
                @click="executeNoteCommand('show-toc')"
                href="javascript:void(0)"
              >
                Table Of Contents
              </a>
            </li>
            <li>
              <a
                v-bind:class="{'disabled': !(isActiveNote)}"
                @click="executeNoteCommand('show-version-control')"
                href="javascript:void(0)"
              >
                Version Control
              </a>
            </li>
            <li>
              <a
                v-bind:class="{'disabled': !(isActiveNote)}"
                @click="executeNoteCommand('show-note-info')"
                href="javascript:void(0)"
              >
                Note Info
              </a>
            </li>
          </ul>
        </li>

        <!-- Runtime Menu -->
        <li> Runtime
          <ul>
            <li>
              <a
                v-bind:class="{'disabled': !(isActiveNote)}"
                @click="executeNoteCommand('run-all')"
                href="javascript:void(0)"
              >
                Run All
              </a>
            </li>
            <li>
              <a
                v-bind:class="{'disabled': !(isActiveNote)}"
                @click="executeNoteCommand('run-before')"
                href="javascript:void(0)"
              >
                Run Before
              </a>
            </li>
            <li>
              <a
                v-bind:class="{'disabled': !(isActiveNote)}"
                @click="executeNoteCommand('run-focused')"
                href="javascript:void(0)"
              >
                Run The Focused Paragraph
              </a>
            </li>
            <li>
              <a
                v-bind:class="{'disabled': !(isActiveNote)}"
                @click="executeNoteCommand('run-after')"
                href="javascript:void(0)"
              >
                Run After
              </a>
            </li>
            <li class="separator"></li>
            <li>
              <a
                @click="openTab('interpreters')"
                href="javascript:void(0)"
              >
                Interpreters
              </a>
            </li>
          </ul>
        </li>

        <!-- Settings -->
        <li> Settings
          <ul>
            <li>
              <a
                @click="openTab('notebook-repository')"
                href="javascript:void(0)"
              >
                Notebook Repository
              </a>
            </li>
            <li>
              <a
                @click="openTab('credentials')"
                href="javascript:void(0)"
              >
                Credentials
              </a>
            </li>
            <li>
              <a
                @click="openTab('configurations')"
                href="javascript:void(0)"
              >
                Configuration
              </a>
            </li>
          </ul>
        </li>

        <!-- Tools Menu -->
        <li> Tools
          <ul>
            <!-- <li
              v-bind:class="{'disabled': !(isActiveNote)}"
              class="submenu"
            >
              Switch Theme
              <ul>
                <li>
                  <a
                    @click="executeNoteCommand('switch-theme-light')"
                    href="javascript: void(0);"
                  >
                    Light
                  </a>
                </li>
                <li>
                  <a
                    @click="executeNoteCommand('switch-theme-dark')"
                    href="javascript: void(0);"
                  >
                    Dark
                  </a>
                </li>
              </ul>
            </li> -->
            <!-- <li>
              <a
                href="javascript: void(0);"
                @click="showPreferences()"
              >
                Preferences
              </a>
            </li> -->
            <li>
              <a
                @click="executeCommand('show-keyboard-shortcuts')"
                class="disabled"
                href="javascript: void(0);"
              >
                Keyboard Shortcuts
              </a>
            </li>
          </ul>
        </li>

        <!-- Help Menu -->
        <li> Help
          <ul>
            <li>
              <a
                href="http://zeppelin.apache.org/community.html"
                target="_blank"
              >
                Mailing list
              </a>
            </li>
            <li>
              <a
                href="https://issues.apache.org/jira/browse/ZEPPELIN"
                target="_blank"
              >
                Report an issue
              </a>
            </li>
            <li>
              <a
                href="https://github.com/apache/zeppelin"
                target="_blank"
              >
                GitHub
              </a>
            </li>
            <li class="separator"></li>
            <li>
              <a
                href="http://zeppelin.apache.org/docs/0.9.0-SNAPSHOT/index.html"
                target="_blank"
              >
                Documentation
              </a>
            </li>
          </ul>
        </li>
      </ul>
    </div>
  </div>
</template>

<script>
import jQueryMenu from '@/components/thirdparty/jquery.menu.js'

export default {
  name: 'TopMenu',
  computed: {
    activeNote () {
      return this.isActiveNote && this.$store.state.TabManagerStore.currentTab
    },
    isActiveNote () {
      return (this.$store.state.TabManagerStore.currentTab &&
          this.$store.state.TabManagerStore.currentTab.type === 'note')
    }
  },
  mounted () {
    jQueryMenu.init()
  },
  methods: {
    showPreferences () {
      this.$store.dispatch('togglePreferences', true)
    },
    openTab (tabName) {
      this.$root.executeCommand('tabs', 'open', { type: tabName })
    },
    executeCommand (command) {
      this.$root.executeCommand(command)
    },
    executeNoteCommand (command) {
      if (this.isActiveNote || ['show-create', 'show-import'].indexOf(command) !== -1) {
        this.$root.executeCommand('note', command)
      }
    },
    showConfirmClearOutput () {
      if (!this.isActiveNote) {
        return
      }

      let that = this
      this.$confirm({
        title: that.$i18n.t('message.note.clear_output_confirm'),
        onOk () {
          that.executeNoteCommand('clear-output')

          that.$message.success(that.$i18n.t('message.note.clear_output_success'), 4)
        },
        onCancel () {}
      })
    }
  }
}
</script>

<style lang="scss">

</style>
