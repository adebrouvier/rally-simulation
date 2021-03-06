package ar.edu.itba.ss;

import java.io.*;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class RallySimulation {

    private final static double MINIMUM_RADIUS = 0.25;
    private final static double MAXIMUM_RADIUS = 0.29;
    private final static double ELASTIC_CONSTANT = 1.2 * Math.pow(10, 5);
    private final static double KT = 2.4 * Math.pow(10, 5);
    private final static double MASS = 80;
    private final static double SOCIAL_FORCE = 2000; // Newton
    private final static double SOCIAL_DISTANCE = 0.08; // Metres
    private final static double ROOM_LENGTH = 100;
    private final static double DOOR_LENGTH = 12;
    private final static double WALL_Y = 0;
    private final static double DRIVING_TIME = 1;
    private static double desiredSpeed = 0.8;
    private final static double CELL_INDEX_RADIUS = 0.8;
    private static CellIndexMethod cellIndexMethod;
    private static PrintWriter statsPrinter;
    private static double exitPosition;
    private final static double WALL_POSITION = 4.0;

    public static void main(String args[]) {
        Configuration config = new CliParser().parseOptions(args);
        exitPosition = config.exitPosition;
        try {
            System.setOut(new PrintStream(new FileOutputStream(config.getOutputFile())));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        try {
            FileWriter fileWriter = new FileWriter(config.getStatsFile());
            statsPrinter = new PrintWriter(fileWriter);
        } catch (IOException e) {
            e.printStackTrace();
        }
        cellIndexMethod = new CellIndexMethod(ROOM_LENGTH, ROOM_LENGTH + WALL_Y, CELL_INDEX_RADIUS);

        createParticles(config.getPedestrians());
        desiredSpeed = config.getDesiredSpeed();
        simulate(config);
        statsPrinter.close();
    }

    private static void createParticles(int numberOfParticles){
        Random r = new Random();

        for (int i = 0; i < numberOfParticles; i++){

            double radius = r.nextDouble() * (MAXIMUM_RADIUS - MINIMUM_RADIUS) + MINIMUM_RADIUS;

            double x;
            double y;

            do {
                x = randomCoord(radius, WALL_POSITION * 2,ROOM_LENGTH - WALL_POSITION * 2);
                y = randomCoord(radius, 1,ROOM_LENGTH - 1);
            }
            while (!validCords(x,y, radius, cellIndexMethod.pedestrians));
            cellIndexMethod.putParticle(new Pedestrian(i+1, new double[]{x, y}, radius, MASS));
        }

    }

    /**
     * Returns a random coordinate between the radius and L - radius.
     * @param radius radius of the particle.
     * @param min min coordinate
     * @return a coordinate in the (radius, L - radius) interval.
     */
    private static double randomCoord(double radius, double min, double max){
        return  min + radius + ((max-min) - 2 * radius) * Math.random();
    }

    /**
     * Checks if there is already a particle on that coordinates.
     * @param x coordinate to check.
     * @param y coordinate to check.
     * @param radius radius of the new particle.
     * @param pedestrians list of pedestrians in the cell.
     * @return true if there is already a particle on the given coordinates, false otherwise.
     */
    private static boolean validCords(double x, double y, double radius, Set<Pedestrian> pedestrians) {

        for (Pedestrian p: pedestrians){
            boolean valid = Math.pow(p.position[0] - x, 2) + Math.pow(p.position[1] - y, 2) > Math.pow(p.radius + radius, 2);
            if (!valid){
                return false;
            }
        }

        return true;
    }

    private static void simulate(Configuration config){
        final double animationTime = config.getFps();
        int iterations = 0;
        printParticles(iterations++);

        double dt = Math.pow(10, -4);
        int dt2 = 0;

        Integrator integrator = new Beeman(dt);
        cellIndexMethod.setNeighbors();

        for (double t = 0; cellIndexMethod.pedestrians.size() > 0; t+=dt){

            integrator.updatePositions(cellIndexMethod.pedestrians);

            updateCells(t);

            cellIndexMethod.setNeighbors();

            integrator.updateSpeeds(cellIndexMethod.pedestrians);

            updateCells(t);

            if (++dt2 % animationTime == 0) {
                printParticles(iterations++);
            }
        }
    }

    public static double[] forces(Pedestrian p) {

        double[] force = new double[2];

        force = horizontalWallForce(p, force, 0);
        force = horizontalWallForce(p, force, ROOM_LENGTH);

        if (!isInTheExit(p)){
            force = lateralCollision(p, force, WALL_POSITION);
            force = lateralCollision(p, force, ROOM_LENGTH - WALL_POSITION);
        }

        for (Pedestrian neighbour : p.neighbors) {

            if (!neighbour.equals(p)){

                /* Pedestrian collision */
                double distance = p.getDistanceTo(neighbour);
                double superposition = p.radius + neighbour.radius - distance;

                double dx = neighbour.position[0] - p.position[0];
                double dy = neighbour.position[1] - p.position[1];
                double ex = (dx / distance);
                double ey = (dy / distance);

                if (superposition > 0) {
                    double relativeSpeed = (p.speed[0] - neighbour.speed[0]) * (-ey) + (p.speed[1] - neighbour.speed[1]) * ex;

                    double normalForce = -ELASTIC_CONSTANT * superposition;

                    force[0] += normalForce * ex;
                    force[1] += normalForce * ey;

                    double tangentForce = -KT * superposition * relativeSpeed;
                    force[0] += tangentForce * (-ey);
                    force[1] += tangentForce * (ex);
                }
                double socialForce = -SOCIAL_FORCE * Math.exp(superposition/ SOCIAL_DISTANCE);
                force[0] += socialForce * ex;
                force[1] += socialForce * ey;
            }
        }
        double[] target = getTarget(p);
        double dxTarget = target[0] - p.position[0];
        double dyTarget = target[1] - p.position[1];
        double mod = Math.sqrt(Math.pow(dxTarget, 2) + Math.pow(dyTarget, 2));
        double ex = dxTarget / mod;
        double ey = dyTarget / mod;
        /* Driving force*/
        force[0] += p.mass * (desiredSpeed * ex - p.speed[0]) / DRIVING_TIME;
        force[1] += p.mass * (desiredSpeed * ey - p.speed[1]) / DRIVING_TIME;

        return force;
    }

    private static double[] getTarget(Pedestrian p) {
        double target[];

        double x,y;
        y = (p.position[1] / ROOM_LENGTH) * DOOR_LENGTH + exitPosition;
        double targetDomain = ROOM_LENGTH / 4 - 10;

        if (p.position[0] < ROOM_LENGTH/2){
            if (!isInTheExit(p)){
                x = WALL_POSITION + 1;
            }else{
                x = (p.position[0] / ROOM_LENGTH / 2) * targetDomain;
                x -= 10;
            }
        }else{
            if (!isInTheExit(p)){
                x = ROOM_LENGTH - WALL_POSITION - 1;
            }else{
                x = ((p.position[0] - ROOM_LENGTH) / ROOM_LENGTH / 2) * targetDomain;
                x += ROOM_LENGTH + 10;
            }
        }
        target = new double[]{x,y};
        return target;
    }

    private static double[] horizontalWallForce(Pedestrian p, double[] force, double wallPosition) {
        double superposition = p.radius - (Math.abs(p.position[1] - wallPosition));

        if (superposition > 0) {

            double dx = 0;
            double dy = wallPosition - p.position[1];

            double mod = Math.sqrt(Math.pow(dx, 2) + Math.pow(dy, 2));
            double ex = (dx / mod);
            double ey = (dy / mod);

            double relativeSpeed = p.speed[0] * (-ey) + p.speed[1] * ex;

            double normalForce = -ELASTIC_CONSTANT * superposition;

            force[0] += normalForce * ex;
            force[1] += normalForce * ey;

            double tangentForce = - KT * superposition * relativeSpeed;
            force[0] += tangentForce * (-ey);
            force[1] += tangentForce * (ex);
        }

        if (p.position[0] + p.radius < ROOM_LENGTH/2 - DOOR_LENGTH/2 &&
                p.position[0] - p.radius > ROOM_LENGTH/2 + DOOR_LENGTH/2){
            force[1] += -SOCIAL_FORCE * Math.exp(-(Math.abs(p.position[1] - wallPosition) - p.radius) / SOCIAL_DISTANCE);
        }
        return force;
    }

    private static double[] lateralCollision(Pedestrian p, double[] force, double wallX){
        double superposition = p.radius - (Math.abs(p.position[0] - wallX));

        double dx = wallX - p.position[0];
        double dy = 0;

        double mod = Math.sqrt(Math.pow(dx, 2) + Math.pow(dy, 2));
        double ex = (dx / mod);
        double ey = (dy / mod);

        if (superposition > 0) {
            double relativeSpeed = p.speed[0] * (-ey) + p.speed[1] * ex;

            double normalForce = -ELASTIC_CONSTANT * superposition;

            force[0] += normalForce * ex;
            force[1] += normalForce * ey;

            double tangentForce = - KT * superposition * relativeSpeed;
            force[0] += tangentForce * (-ey);
            force[1] += tangentForce * (ex);
        }

        force[0] += -SOCIAL_FORCE * Math.exp(-(Math.abs(p.position[0] - wallX) - p.radius) / SOCIAL_DISTANCE) * ex;
        return force;
    }

    private static boolean isInTheExit(Pedestrian p) {
        return p.position[1] > (p.radius + exitPosition) && p.position[1] < (exitPosition + DOOR_LENGTH - p.radius);
    }

    private static void printParticles(int iteration){
        System.out.println(cellIndexMethod.pedestrians.size() + 4);
        System.out.println(iteration);
        for (Pedestrian p: cellIndexMethod.pedestrians)
            System.out.println(p.position[0] + "\t" + p.position[1] + "\t" + p.radius + "\t" + p.getSpeedModule());
        /* Print exits */
        System.out.println(WALL_POSITION + "\t" + exitPosition + "\t" + 0 + "\t" + 0);
        System.out.println(WALL_POSITION + "\t" + (exitPosition + DOOR_LENGTH) + "\t" + 0 + "\t" + 0);
        System.out.println((ROOM_LENGTH - WALL_POSITION) + "\t" + exitPosition + "\t" + 0 + "\t" + 0);
        System.out.println((ROOM_LENGTH - WALL_POSITION) + "\t" + (exitPosition + DOOR_LENGTH) + "\t" + 0 + "\t" + 0);
    }

    private static void updateCells(double time){
        List<Pedestrian> removePedestrians = new LinkedList<>();
        for (Pedestrian p: cellIndexMethod.pedestrians) {
            if (!cellIndexMethod.putParticle(p)){
                removePedestrians.add(p);
            }
        }
        for (Pedestrian p: removePedestrians){
            cellIndexMethod.pedestrians.remove(p);
            statsPrinter.println(time);
        }
    }

}
