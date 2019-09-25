import Vue from 'vue'
import VueI18n from 'vue-i18n'

Vue.use(VueI18n)

// Usage
// template - $t("message.hello")
// js - this.$i18n.t('message.hello')

export const i18n = new VueI18n({
  locale: 'en',
  messages: {
    en: {
      message: {
        notebooks: {
          create_success: 'Notebook created successfully.',
          import_click_or_drag: 'Click or drag a file to upload',
          import_success: 'Notebook imported successfully.',
          import_json_type_error: 'You can only upload JSON file!',
          import_json_size_error: 'File must be smaller than 1 MB!',
          clone_success: 'Notebook cloned successfully.',
          clear_output_confirm: 'Do you want to clear the ouput for all the paragraphs?',
          clear_output_success: 'Output cleared successfully for all the paragraphs.',
          move_to_rb_confirm: 'Do you want to delete this Notebook?',
          move_to_rb_content: 'This will move the notebook to Recycle Bin and you can still recover it.',
          move_to_rb_success: 'Notebook moved to recycle bin successfully.'
        }
      }
    },
    ja: {
      message: {
        hello: 'こんにちは、世界'
      }
    }
  }
})
