## Example of How to Run in PowerShell
cd "C:\Users\Blyx\Documents\TAR UMT\Year 1 Semester 3\AMCS2204 Object Oriented Programming Techniques\Projects\Assignment"
java --enable-native-access=ALL-UNNAMED -cp "bin;lib/sqlite-jdbc-3.51.1.0.jar" Main
cd ~

## Assigned Question Summary
Smart Library Management System

Items: books, journals, digital resources
Users/Patrons: students, faculty, public members

Actions Available:
- Organize catalog by genre, author, ISBN
- Search if book is "Available" or "On Loan"
- If book is being borrowed, users can place reservation requests
- When a book is borrowed, system records issue date and calculates due date
- When returned, system checks if overdue; calculates fine based on number of days late.
- Librarians use the system to add new acquisitions, remove damaged books, and audit inventory.
- System also tracks lost books and manages fines paid.

Optional features:
- e-book reader integration
- study room booking
- inter-library loan requests

## Ideal Path / Directories Structure
src
 └── library
      ├── Main.java
      ├── model
      │    ├── LibraryItem.java        // abstract base
      │    ├── Book.java
      │    ├── Journal.java
      │    ├── DigitalResource.java
      │    ├── User.java               // abstract base
      │    ├── Student.java
      │    ├── Faculty.java
      │    ├── PublicMember.java
      │    ├── Loan.java
      │    ├── Reservation.java
      │    ├── Fine.java
      │    └── Enums (Status, Genre, UserType, etc. if needed)
      │
      ├── service
      │    ├── CatalogService.java     // search, list by filters
      │    ├── LoanService.java        // borrow/return, due dates
      │    ├── ReservationService.java // place/fulfill reservations
      │    ├── FineService.java        // calculate & record fines
      │    └── LibraryService.java     // high-level façade (uses others)
      │
      ├── persistence
      │    ├── DatabaseManager.java    // handles SQLite connection
      │    ├── ItemRepository.java
      │    ├── UserRepository.java
      │    ├── LoanRepository.java
      │    ├── ReservationRepository.java
      │    └── FineRepository.java
      │
      └── ui
           ├── ConsoleMenu.java        // main text UI
           ├── LibrarianMenu.java
           └── PatronMenu.java


## Getting Started

Welcome to the VS Code Java world. Here is a guideline to help you get started to write Java code in Visual Studio Code.

## Folder Structure

The workspace contains two folders by default, where:

- `src`: the folder to maintain sources
- `lib`: the folder to maintain dependencies

Meanwhile, the compiled output files will be generated in the `bin` folder by default.

> If you want to customize the folder structure, open `.vscode/settings.json` and update the related settings there.

## Dependency Management

The `JAVA PROJECTS` view allows you to manage your dependencies. More details can be found [here](https://github.com/microsoft/vscode-java-dependency#manage-dependencies).



