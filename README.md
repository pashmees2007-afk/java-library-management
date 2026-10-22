# Library Management System

[![Build and test](https://github.com/pashmees2007-afk/java-library-management/actions/workflows/build.yml/badge.svg)](https://github.com/pashmees2007-afk/java-library-management/actions/workflows/build.yml)

A desktop app for running a small library — a school, college or community one.
You keep a list of the books you own and a list of the people who may borrow
them, and the app tracks who has what, when it is due back, and what they owe if
it comes back late.

Everything is saved in one file on your computer (`library.db`). There is no
server to set up, no account to create and no internet connection needed.

**Written in Java**, with a **Swing** window for the interface and **SQLite**
for storage. It needs one extra file (a database driver) which is already
included, so you can download the project and run it straight away.

## What you can do with it

- Keep a catalogue of books — title, author, ISBN, category, shelf code, and how
  many copies you own
- Register members and set how many books each may borrow at once
- Issue a book to a member, with the due date worked out for you
- Take books back, and charge (or forgive) a fine when they are late
- Renew a loan that is not yet overdue
- See at a glance what is overdue, who has it, and how to contact them
- Export your books, members or loan history to CSV for Excel
- Back up the whole library to a single file

## Screenshots

### Dashboard

The first screen. It shows how many books and members you have, how many copies
are out on loan, what is overdue, and the total unpaid fines. The red list is the
one that needs attention — it includes each borrower's phone number so you can
chase them.

![The dashboard](docs/screenshots/dashboard.png)

### Catalogue

Every book you own. Type in the search box to find something by title, author,
ISBN or publisher, or narrow the list to one category. Click a row to edit it in
the form on the right; the "New book" button clears the form for a fresh entry.
Books with no copies left on the shelf are shown in orange.

![The catalogue tab](docs/screenshots/catalogue.png)

### Members

Everyone who can borrow. "On loan" is how many books they are holding right now
and "Dues" is what they owe. Click a member to see every book they have ever
borrowed in the panel underneath.

![The members tab](docs/screenshots/members.png)

### Circulation desk

Where the day-to-day work happens. Pick a member, pick a book that is on the
shelf, and press "Issue book". The list on the right shows the current loans;
select one to take it back, renew it, or settle a fine. Overdue rows are red.

![The circulation desk](docs/screenshots/circulation.png)

### Settings

Change the library's name, how long a loan lasts and how much the fine is per
day. Also where you export to CSV or back up the database.

![The settings tab](docs/screenshots/settings.png)

## Getting started

You need **Java 17 or newer**. To check, open a terminal and run:

```bash
java -version
```

If that prints a version number you are ready. If it says the command was not
found, install a JDK first — [Adoptium](https://adoptium.net) is a free and
straightforward choice.

Then download this project (the green **Code** button → **Download ZIP**, or
`git clone`), open a terminal in the project folder, and run:

```bash
run.bat --demo
```

That does three things: compiles the code, fills the library with example data
so there is something to look at, and opens the window.

Leave off `--demo` to start with an empty library of your own:

```bash
run.bat
```

The first run creates `library.db` in the project folder. That file **is** your
library — everything you add is saved there immediately. Copy it to move your
library to another computer.

### A two-minute tour

With the demo data loaded, try this:

1. Open the **Circulation** tab.
2. In the Member box type `Asha`, then click her name in the list below.
3. In the Book box type `Hobbit`, then click the title.
4. Press **Issue book**. The loan appears in the list on the right with its due
   date, and the catalogue now shows one fewer copy on the shelf.
5. Select that new row and press **Accept return**. The copy goes back on the
   shelf.
6. Now change the filter at the top right to **Overdue**. These loans are
   already late in the demo data. Select one and press **Accept return** — the
   app tells you what the fine is and asks whether to charge it.

### Other options

| Command | What it does |
| --- | --- |
| `run.bat` | Open the app on `library.db` |
| `run.bat --demo` | Fill an empty library with example data first |
| `run.bat --db school.db` | Use a different library file |
| `run.bat --help` | List the options and exit |
| `build.bat` | Just compile, do not run |
| `test.bat` | Compile and run all the tests |

### Running it on Mac or Linux

The `.bat` files are for Windows, but the code itself runs anywhere Java does.
Use these two commands instead (note the `:` instead of `;` in the classpath):

```bash
find src/main/java -name '*.java' > sources.txt && javac -d build/classes -cp lib/sqlite-jdbc-3.50.3.0.jar @sources.txt
```

```bash
java -cp "build/classes:lib/sqlite-jdbc-3.50.3.0.jar" com.library.Main --demo
```

## How borrowing works

The app will not let you break these rules, so you cannot accidentally lend a
book you do not have:

- **A copy has to be on the shelf.** If you own three copies and all three are
  out, the next person has to wait.
- **One copy per person per title.** The same member cannot take two copies of
  the same book.
- **Everyone has a limit.** It depends on the kind of membership: Students 3
  books, Teachers 6, Staff 5, Public 2. You can set a different limit on any
  individual member's record, and that one wins.
- **Overdue books block new ones.** If a member is already late with something,
  they cannot borrow again until it is sorted out.
- **Inactive members cannot borrow.** Untick "Membership active" to suspend
  someone without deleting their history.
- **Renewals are limited.** A loan can be renewed twice, and only while it is not
  already overdue. After that the book has to come back.
- **Nothing in use can be deleted.** A book or member with an open loan cannot be
  removed until the book is returned.

### How a fine is worked out

A fine is simply **days late × the daily rate**. With the default settings — a
14-day loan and a fine of 2.00 per day:

> A book borrowed on 1 October is due back on 15 October. It is returned on 18
> October, which is 3 days late, so the fine is **6.00**.

While a book is still out and overdue the fine keeps growing, and you can see the
running total on the dashboard and against the member. The amount is only written
down when you accept the return. At that point you can charge it, or press
**Return & waive fine** to let it go. Charged fines stay marked unpaid until you
press **Mark fine paid**.

You can change the loan length and the daily rate on the Settings tab. New
settings apply to new loans and to fines still being counted; loans already
issued keep the due date they were given.

## Keyboard shortcuts

| Keys | Action |
| --- | --- |
| `Ctrl+1` … `Ctrl+5` | Jump straight to a tab |
| `Ctrl+F` | Jump to the search box |
| `Ctrl+N` | Start a new book or member |
| `F5` | Reload everything from the database |
| `F1` | Show the shortcut list |
| `Esc` | Clear the search box |
| `Ctrl+Q` | Quit |

Double-clicking a row opens it for editing, or accepts the return at the
circulation desk. Clicking a column heading sorts by that column; click it again
to reverse the order.

## How the project is put together

The code is split into two halves, which is the main idea worth knowing:

- **The data layer** (`db/LibraryDatabase.java`) talks to the database and owns
  every rule above. If a rule is broken it refuses and explains why.
- **The interface** (`ui/`) only draws screens and passes what you typed to the
  data layer. It never decides anything itself.

Keeping them apart means a rule cannot be skipped by accident from a button, and
the rules can be tested without opening a window.

```
build.bat / run.bat / test.bat   compile, run, test
lib/sqlite-jdbc-3.50.3.0.jar     the database driver (the only dependency)
src/main/java/com/library/
    Main.java                    starts the app, reads the command line
    SampleData.java              the example books, members and loans
    model/                       plain descriptions of a Book, Member, Loan
    db/                          the database and all the borrowing rules
    ui/                          the window, the five tabs, and the reusable
                                 pieces they are built from (tables, forms,
                                 search boxes, the chart)
src/test/java/com/library/       the tests
docs/screenshots/                the images in this file
```

## Where your data lives

Everything sits in one SQLite file with four tables:

| Table | What it holds |
| --- | --- |
| `books` | One row per title, with how many copies you own and how many are on the shelf |
| `members` | One row per person, with contact details and borrowing limit |
| `loans` | One row per issue: which book, which member, out date, due date, return date, fine |
| `settings` | The library name, loan length and fine rate |

Each change is saved in a transaction, which means that if something goes wrong
halfway through, nothing is half-saved — the copy counts can never drift out of
step with the loans.

You can open `library.db` in any SQLite tool if you want to look at it directly,
though the app is happier if you let it do the writing.

## Tests

```bash
test.bat
```

70 tests, and no extra software needed to run them.

- **47 tests** check the rules and the database: that a fine is worked out
  correctly, that the copy counts stay right, that a book on loan cannot be
  deleted, that the data is still there after closing and reopening the file.
- **23 tests** open the real window behind the scenes and click through it —
  adding a book, catching a bad email address, issuing and returning, charging
  and waiving a fine — so the screens are tested too, not just the logic.

Every push also runs both suites on GitHub Actions: on Windows with JDK 21
through `build.bat` and `test.bat`, and on Linux with JDK 17 (the oldest version
this project supports) using plain `javac`, with the window tests running on a
virtual display.

## If something goes wrong

**"'java' is not recognized" / "command not found"** — Java is not installed, or
not on your PATH. Install a JDK from [Adoptium](https://adoptium.net), then open
a new terminal.

**"Missing lib\sqlite-jdbc-3.50.3.0.jar"** — the database driver is not where it
should be. Download it from
[Maven Central](https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/3.50.3.0/)
and put it in the `lib` folder.

**The window opens but the lists are empty** — that is a brand new, empty
library. Add a book and a member, or close it and run `run.bat --demo` to load
the example data.

**A change does not show up** — press `F5` to reload every tab from the database.

**You want to start over** — close the app and delete `library.db`. The next run
creates a fresh one. Make a copy first if you might want it back.

## License

MIT — see [LICENSE](LICENSE). You are free to use, modify and distribute this
code, including commercially, as long as the copyright notice travels with it.

The bundled `lib/sqlite-jdbc-3.50.3.0.jar` is third-party software by Taro L.
Saito and contributors, distributed under the Apache License 2.0. SQLite itself
is in the public domain.
