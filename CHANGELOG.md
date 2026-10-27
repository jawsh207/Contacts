# Changelog

Each release's notes on the GitHub Releases page come from its section here. Newest first; headings are `## <version name>`, matching line 2 of `version.txt`.

## 1.2.5

**Fixes** - Importing contacts from a vCard (.vcf) file now handles birthdays without a year correctly, and no longer splits names or addresses that contain a comma or semicolon. - In the contact editor, an empty Notes field is the right height again and responds to a tap anywhere on it. - Apps that add or look up contacts through Contacts no longer crash. - First, middle and last name fields now open the name keyboard. - Pressing Back on the Highlights or Organize tab now takes you to the Contacts tab instead of closing the app.

- Add a Build release workflow
- Bring in upstream bug fixes
- Move assistant notes to AGENTS.md; no assistant attribution

## 1.2.4

**Search bars**
- The search bars on the Contacts, Highlights and Organize tabs now have the same margin on both sides, in line with the lists and cards below them.

## 1.2.3

**Highlights and Organize tabs**
- Section headings (Favorites, For you and Recents on Highlights; Labels and Fix & manage on Organize) now use your theme colour, like Favorites on the Contacts tab.

**Birthday cards**
- A large cake now fills the right side of each card, with the photo, name, date and Call and Message buttons beside it. On narrow screens the buttons wrap onto two lines.

## 1.2.2

**Contact photos**
- Contact photos in the contacts list, Highlights Recents and Merge duplicates are larger (48dp, up from 40dp), so contacts list rows are a little taller.

## 1.2.1

**Highlights tab**
- Favorites now appear in Material 3 Expressive shapes (cookie, sunny, clover, pentagon, arch, gem and puffy) instead of circles, matching the Dialtone dialer. Each person keeps the same shape and colour, and photos fill the shape.

## 1.2

**Highlights tab**
- Tapping a call in Recents opens the dialer with that number filled in, and tapping a text opens the conversation in your messaging app. Recently viewed contacts still open the contact.
- Fixed: calls and texts with contacts that had been merged no longer disappear from Recents.

## 1.1

**Organize tab**
- New Merge duplicates, under Labels: finds contacts in the same account with the same name, phone number or email, and merges each set into one contact that keeps every distinct number, email and detail. Merge suggestions one at a time or all at once, or dismiss the ones that aren't duplicates.

**Your info**
- The text under the QR code now explains that you show the code to share your contact info.

## 1.0

First release of this fork of the Contacts app from vayun-mathur/Modern-Apps, restyled after Google Contacts.

**Look and feel**
- Material 3 Expressive theme with dynamic colour, Google Contacts' surface layering, and all text in Google Sans Flex
- No hamburger menus: bottom navigation, with secondary actions in ⋮ menus
- New app icon: the GrapheneOS Contacts glyph in white on Common Teal
- Contacts list without the fast-scroll bar

**Highlights tab**
- Favorites row with large photos
- Upcoming birthday cards with ages and quick Call / Message buttons
- Recents: recently viewed contacts, or (opt-in) calls and texts with saved contacts, with call/text icons and message previews

**Contact page**
- Large round photo over a blurred copy of it, with a ring separating the two; contacts without a photo get a gradient of their colour
- Contact info and Labels cards in Google Contacts' style; Share, Set ringtone, Move and Delete in the menu

**Your info**
- Uses Android's Me profile, with a QR code to share your contact card

**Birthday calendar**
- Calendar named "Contact Birthdays", with the age each person turns in the event title

**Other**
- SIM contact support removed
- Editor: more space between field groups, so "Add phone" no longer crowds the email field
