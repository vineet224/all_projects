package interviews.interview;

import java.util.*;


/*
Simple Cricket Scoreboard Machine Coding Version

Features:
- two teams
- batting order
- strike rotation
- wickets
- over change
- innings change
- scorecard
*/


class Player {

    int id;

    String name;

    int runs = 0;

    int ballsPlayed = 0;

    int fours = 0;

    int sixes = 0;

    static int idCounter = 1;

    public Player(String name) {

        this.id = idCounter++;

        this.name = name;
    }
}


class Team {

    int id;

    String name;

    Queue<Player> battingOrder;

    int totalRuns = 0;

    int wickets = 0;

    static int idCounter = 1;

    public Team(
            String name,
            List<Player> players
    ) {

        this.id = idCounter++;

        this.name = name;

        this.battingOrder =
                new LinkedList<>(players);
    }

    public Player getNextPlayer() {

        if (battingOrder.isEmpty()) {

            return null;
        }

        return battingOrder.poll();
    }
}


class MatchManager {

    Team team1;

    Team team2;

    Team battingTeam;

    Team bowlingTeam;

    Player striker;

    Player nonStriker;

    int totalOvers;

    int currentOver = 0;

    int currentBall = 0;

    int innings = 1;

    public MatchManager(
            Team team1,
            Team team2,
            int totalOvers
    ) {

        this.team1 = team1;

        this.team2 = team2;

        this.totalOvers = totalOvers;

        battingTeam = team1;

        bowlingTeam = team2;

        striker = battingTeam.getNextPlayer();

        nonStriker = battingTeam.getNextPlayer();
    }


    public void ball(int runs) {

        if (matchEnded()) {

            System.out.println("Match ended");

            return;
        }

        currentBall++;

        battingTeam.totalRuns += runs;

        striker.runs += runs;

        striker.ballsPlayed++;

        if (runs == 4) {

            striker.fours++;
        }

        if (runs == 6) {

            striker.sixes++;
        }

        System.out.println(
                striker.name +
                        " scored " +
                        runs + " run"
        );

        // rotate strike
        if (runs % 2 == 1) {

            changeStrike();
        }

        // over completed
        if (currentBall == 6) {

            currentBall = 0;

            currentOver++;

            System.out.println(
                    "Over completed: " +
                            currentOver
            );

            changeStrike();
        }

        checkInningsEnd();
    }


    public void wicket() {

        if (matchEnded()) {

            return;
        }

        currentBall++;

        striker.ballsPlayed++;

        battingTeam.wickets++;

        System.out.println(
                striker.name + " OUT"
        );

        Player nextPlayer =
                battingTeam.getNextPlayer();

        if (nextPlayer == null) {

            System.out.println(
                    battingTeam.name +
                            " all out"
            );

            checkInningsEnd();

            return;
        }

        striker = nextPlayer;

        // over completed
        if (currentBall == 6) {

            currentBall = 0;

            currentOver++;

            changeStrike();
        }

        checkInningsEnd();
    }


    private void changeStrike() {

        Player temp = striker;

        striker = nonStriker;

        nonStriker = temp;
    }


    private void checkInningsEnd() {

        if (currentOver >= totalOvers
                || battingTeam.wickets >= 10) {

            if (innings == 1) {

                changeInnings();
            }
            else {

                System.out.println(
                        "Match finished"
                );

                printMatchResult();
            }
        }
    }


    public void changeInnings() {

        System.out.println(
                "\nFirst innings ended\n"
        );

        innings = 2;

        currentOver = 0;

        currentBall = 0;

        battingTeam = team2;

        bowlingTeam = team1;

        striker = battingTeam.getNextPlayer();

        nonStriker =
                battingTeam.getNextPlayer();

        System.out.println(
                "Second innings started\n"
        );
    }


    public boolean matchEnded() {

        return innings == 2
                &&
                (currentOver >= totalOvers
                        || battingTeam.wickets >= 10);
    }


    public void printScoreCard() {

        System.out.println(
                "\nScoreCard"
        );

        System.out.println(
                battingTeam.name +
                        " -> " +
                        battingTeam.totalRuns +
                        "/" +
                        battingTeam.wickets
        );

        System.out.println(
                "Overs: " +
                        currentOver +
                        "." +
                        currentBall
        );

        System.out.println(
                "Striker: " +
                        striker.name +
                        " (" +
                        striker.runs + ")"
        );

        System.out.println(
                "Non-Striker: " +
                        nonStriker.name +
                        " (" +
                        nonStriker.runs + ")"
        );

        System.out.println();
    }


    public void printMatchResult() {

        System.out.println(
                "\nFinal Result"
        );

        System.out.println(
                team1.name +
                        " score: " +
                        team1.totalRuns
        );

        System.out.println(
                team2.name +
                        " score: " +
                        team2.totalRuns
        );

        if (team1.totalRuns > team2.totalRuns) {

            System.out.println(
                    team1.name + " won"
            );
        }
        else if (team2.totalRuns > team1.totalRuns) {

            System.out.println(
                    team2.name + " won"
            );
        }
        else {

            System.out.println("Match Draw");
        }
    }
}



public class Solution2 {

    public static void main(String[] args) {

        List<Player> indiaPlayers =
                Arrays.asList(
                        new Player("Virat"),
                        new Player("Rohit"),
                        new Player("Gill"),
                        new Player("KL Rahul"),
                        new Player("Hardik")
                );

        List<Player> ausPlayers =
                Arrays.asList(
                        new Player("Warner"),
                        new Player("Smith"),
                        new Player("Maxwell"),
                        new Player("Head"),
                        new Player("Marsh")
                );

        Team india =
                new Team(
                        "India",
                        indiaPlayers
                );

        Team australia =
                new Team(
                        "Australia",
                        ausPlayers
                );

        MatchManager match =
                new MatchManager(
                        india,
                        australia,
                        2
                );



        // FIRST INNINGS

        match.ball(1);

        match.ball(4);

        match.ball(2);

        match.wicket();

        match.ball(6);

        match.ball(1);

        match.printScoreCard();




        // second over

        match.ball(2);

        match.ball(1);

        match.ball(4);

        match.ball(0);

        match.wicket();

        match.ball(1);

        match.printScoreCard();




        // SECOND INNINGS

        match.ball(1);

        match.ball(1);

        match.ball(6);

        match.ball(4);

        match.wicket();

        match.ball(2);

        match.printScoreCard();
    }
}