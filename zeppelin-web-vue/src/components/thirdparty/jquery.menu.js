let activated = false

let settings = {
  disabledClass: 'disabled',
  submenuClass: 'submenu'
}

let mask = '<div class="menu-top-mask"/>'
let timeOut

export default {
  init () {
    var that = this
    window.$.extend(settings)

    // let window.$mask = window.$('.menu-top-mask')

    window.$('ul.main-menu > li').click(function (event) {
      var target = window.$(event.target)
      if (target.hasClass(settings.disabledClass) ||
          target.parents().hasClass(settings.disabledClass) ||
          target.hasClass(settings.submenuClass)
      ) {
        return
      }

      that.toggleMenuItem(window.$(this))
    })

    window.$('ul.main-menu > li > ul li').click(function (event) {
      if (window.$(this).children().length > 0) {
        // event.preventDefault() - this was preventing external link opening
        that.toggleSubMenu(window.$(this))
      }
    })

    window.$('ul.main-menu > li').mouseenter(function () {
      if (activated && window.$(this).hasClass('active-menu') === false) {
        that.toggleMenuItem(window.$(this))
      }
    })

    window.$('ul.main-menu > li > ul li').mouseenter(function (e) {
      // Hide all other opened submenus in same level of this item
      let $el = window.$(e.target)
      if ($el.hasClass('separator')) return
      clearTimeout(timeOut)
      var parent = $el.closest('ul')
      parent.find('ul.active-sub-menu').each(function () {
        if (window.$(this) !== $el) {
          window.$(this).removeClass('active-sub-menu').hide()
        }
      })

      // Show submenu of selected item
      if ($el.children().length > 0) {
        timeOut = setTimeout(function () { that.toggleSubMenu($el) }, 500)
      }
    })

    window.$('ul.main-menu > li > ul li').each(function () {
      if (window.$(this).children('ul').length > 0) {
        window.$(this).addClass(settings.submenuClass)
      }
    })

    window.$('ul.main-menu li.' + settings.disabledClass).bind('click', function (e) {
      e.preventDefault()
    })

    window.$(document).keyup(function (e) {
      if (e.keyCode === 27) {
        that.closeMainMenu()
      }
    })

    window.$(document).bind('click', function (event) {
      var target = window.$(event.target)
      if (!target.hasClass('active-menu') && !target.parents().hasClass('active-menu')) {
        that.closeMainMenu()
      }
    })
  },
  toggleSubMenu (el) {
    if (el.hasClass(settings.disabledClass)) {
      return
    }

    var submenu = el.find('ul:first')
    var paddingLeft = parseInt(el.css('padding-right').replace('px', ''), 10)
    var borderTop = parseInt(el.css('border-top-width').replace('px', ''), 10)
    borderTop = !isNaN(borderTop) ? borderTop : 1
    var top = el.position().top - borderTop

    submenu.css({
      position: 'absolute',
      top: top + 'px',
      left: el.width() + paddingLeft + 'px',
      zIndex: 1000
    })

    submenu.addClass('active-sub-menu')
    submenu.show()
  },
  toggleMenuItem (el) {
    // Hide all open submenus
    window.$('.active-sub-menu').removeClass('active-sub-menu').hide()

    window.$('.menu-top-mask').remove()

    let submenu = el.find('ul:first')
    let top = parseInt(el.css('padding-bottom').replace('px', ''), 10) + parseInt(el.css('padding-top').replace('px', ''), 10) +
                el.position().top +
                el.height()

    submenu.prepend(window.$(mask))
    let $mask = window.$('.menu-top-mask')
    let maskWidth = el.width() +
                    parseInt(el.css('padding-left').replace('px', ''), 10) +
                    parseInt(el.css('padding-right').replace('px', ''), 10)

    $mask.css({ position: 'absolute',
      top: '-1px',
      width: (maskWidth) + 'px'
    })

    submenu.css({
      position: 'absolute',
      top: top + 'px',
      left: el.offset().left + 'px',
      zIndex: 100
    })

    submenu.stop().toggle()
    activated = submenu.is(':hidden') === false

    !activated ? el.removeClass('active-menu') : el.addClass('active-menu')

    if (activated) {
      window.$('.active-menu').each(function () {
        if (window.$(this).offset().left !== el.offset().left) {
          window.$(this).removeClass('active-menu')
          window.$(this).find('ul:first').hide()
        }
      })
    }
  },
  closeMainMenu () {
    activated = false
    window.$('.active-menu').find('ul:first').hide()
    window.$('.active-menu').removeClass('active-menu')
    window.$('.active-sub-menu').hide()
  }
}
