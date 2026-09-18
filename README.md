# Travel Expense Manager

A console-based Java application for planning a one-way trip and tracking expenses segment by segment.

The program helps you:

- Choose an origin, destination, transport mode, number of travellers, and number of nights.
- Estimate Low, Medium, and High per-person budget bands.
- Build a segment-by-segment travel plan.
- View suggested food, accommodation, activity, and local transport options.
- Record actual group spending for every segment.
- Receive alerts when spending is trending high or exceeds the planned range.
- See cheaper alternatives after overspending.

## Requirements

- Java Development Kit (JDK) 11 or newer.
- A terminal or VS Code with Java support.

Check that Java is installed:

```bash
java -version
javac -version
```

## Compile and Run

Open a terminal in the project folder:

```bash
cd "/Users/tushitapathak/Downloads/TravelExpenseManager"
javac TravelExpenseMvp.java
java TravelExpenseMvp
```

The source file contains all classes used by the application, so only `TravelExpenseMvp.java` is needed for compilation.

To compile and run in one command:

```bash
javac TravelExpenseMvp.java && java TravelExpenseMvp
```

## How to Use

1. Enter the starting point and destination.
2. Select a transport mode.
3. Accept a stored route distance or enter an approximate distance.
4. Enter the number of travellers and destination nights.
5. Review the calculated Low, Medium, and High budget bands.
6. Choose the budget band to plan against.
7. Review the generated segments and suggestions.
8. Enter actual group spending for each listed category.
	Press Enter to accept the planned midpoint for a category.
9. Read the tracker feedback and final trip summary.

## Budget and Tracking Details

- Budget ranges are estimates, not live prices.
- Transport, food, accommodation, activities, and local transport are estimated separately.
- Displayed plan amounts include a 12% buffer.
- Category and segment plan amounts are shown per person and for the whole group.
- Actual spending is entered as a group amount.
- The final summary compares total group spending with the selected budget band.

## Stored Routes

The application has distances for these city pairs:

- Delhi - Jaipur
- Delhi - Manali
- Delhi - Agra
- Mumbai - Goa
- Bhopal - Indore
- Bhopal - Goa
- Bhopal - Delhi
- Bengaluru - Goa

For any other route, enter the approximate distance manually.

## Project Structure

```text
TravelExpenseManager
 -TravelExpenseMvp.java
 -README.md
 -Running.md

```

The Java file contains the console entry point, trip model, route lookup, budget engine, segment planner, suggestion catalog, expense tracker, and input handling.

## Cleaning Compiled Files

Compilation creates `.class` files in the project folder. Remove them with:

```bash
rm -f *.class
