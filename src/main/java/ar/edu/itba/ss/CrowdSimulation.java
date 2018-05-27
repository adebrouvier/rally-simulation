package ar.edu.itba.ss;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;

public class CrowdSimulation {

    private final static int NUMBER_OF_PARTICLES = 10;
    private final static double MINIMUM_RADIUS = 0.25;
    private final static double MAXIMUM_RADIUS = 0.29;
    private final static double ELASTIC_CONSTANT = 1.2 * Math.pow(10, 5);
    private final static double VISCOUS_CONSTANT = 2.4 * Math.pow(10, 5);
    private final static double MASS = 60;
    private final static double SOCIAL_FORCE = 2000; // Newton
    private final static double SOCIAL_DISTANCE = 0.08; // Metres
    private final static double[] TARGET_POSITION = new double[]{0,0};
    private final static double DRIVING_TIME = 0.5;

    public static void main(String args[]) throws CloneNotSupportedException {
        List<Particle> particles = createParticles(NUMBER_OF_PARTICLES);
        simulate(particles);
    }

    private static List<Particle> createParticles(int numberOfParticles){
        List<Particle> particles = new LinkedList<>();
        Random r = new Random();
//        double x = -10;
//        double y = -10;
//        for(int i = 0; i < numberOfParticles; i++){
//            double radius = r.nextDouble() * (MAXIMUM_RADIUS - MINIMUM_RADIUS) + MINIMUM_RADIUS;
//            if (x + radius >= 10)
//                x = -10 + radius;
//            if (y + radius >= 10)
//                y = -10 + radius;
//            particles.add(new Particle(i, new double[]{x,y}, radius, MASS)); // TODO: Ask mass.
//            x += MAXIMUM_RADIUS * 2;
//            y += MINIMUM_RADIUS * 2;
//        }
        double radius = r.nextDouble() * (MAXIMUM_RADIUS - MINIMUM_RADIUS) + MINIMUM_RADIUS;
        particles.add(new Particle(1, new double[]{0,-1}, radius, MASS));
        radius = r.nextDouble() * (MAXIMUM_RADIUS - MINIMUM_RADIUS) + MINIMUM_RADIUS;
        particles.add(new Particle(2, new double[]{0,1}, radius, MASS));

        return particles;
    }

    private static void simulate(List<Particle> particles) throws CloneNotSupportedException {
        final double TIME = 20;
        final int TIME2 = 10;
        int iterations = 0;
        printParticles(iterations++, particles);

        double dt = 0.01*Math.sqrt(MASS/ELASTIC_CONSTANT);
        int dt2 = 0;

        Integrator integrator = new Beeman(dt);

        for (double t = 0; t < TIME; t+=dt){

            List<Particle> clones = cloneParticles(particles);
            integrator.updatePositions(particles, clones);

            integrator.updateSpeeds(particles, clones);

            if (++dt2 % TIME2 == 0) {
                printParticles(iterations++, particles);
            }
        }
    }

    public static double[] forces(Particle p, List<Particle> particles) {

        double[] force = new double[2];

        for (Particle neighbour : particles) {

            if (!neighbour.equals(p)){

                /* Particle collision */
                double superposition = p.radius + neighbour.radius - p.getDistanceTo(neighbour);

                double dx = neighbour.position[0] - p.position[0];
                double dy = neighbour.position[1] - p.position[1];
                double mod = Math.sqrt(Math.pow(dx, 2) + Math.pow(dy, 2));
                double ex = (dx / mod);
                double ey = (dy / mod);

                if (superposition > 0) {
                    double relativeSpeed = (p.speed[0] - neighbour.speed[0]) * ex + (p.speed[1] - neighbour.speed[1]) * ey;

                    double normalForce = -ELASTIC_CONSTANT * superposition - VISCOUS_CONSTANT * relativeSpeed;

                    force[0] += normalForce * ex;
                    force[1] += normalForce * ey;
                }

                /* Social force */
                force[0] += SOCIAL_FORCE * Math.exp(-Math.abs(dx) / SOCIAL_DISTANCE) * ex;
                force[1] += SOCIAL_FORCE * Math.exp(-Math.abs(dy) / SOCIAL_DISTANCE) * ey;

                double desiredSpeed = 0.8;
                double dxTarget = TARGET_POSITION[0] - p.position[0];
                double dyTarget = TARGET_POSITION[1] - p.position[1];
                mod = Math.sqrt(Math.pow(dxTarget, 2) + Math.pow(dyTarget, 2));
                ex = dxTarget / mod;
                ey = dyTarget / mod;
                /* Driving force*/
                force[0] += p.mass * (desiredSpeed * ex - p.speed[0]) / DRIVING_TIME;
                force[1] += p.mass * (desiredSpeed * ey - p.speed[1]) / DRIVING_TIME;
            }
        }

        return force;
    }

    private static void printParticles(int iteration, List<Particle> particles){
        System.out.println(particles.size());
        System.out.println(iteration);
        for (Particle p: particles)
            System.out.println(p.position[0] + "\t" + p.position[1] + "\t" + p.radius);
    }

    private static List<Particle> cloneParticles(List<Particle> particles) throws CloneNotSupportedException {
        List<Particle> clones = new LinkedList<>();
        for (Particle p: particles){
            clones.add(p.getClone());
        }
        return clones;
    }
}