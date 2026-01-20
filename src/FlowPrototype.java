
/**
 * @author Sinray Yang
 */
public class FlowPrototype {

    // -------------------------
    // Simulation parameters
    // -------------------------
    static final int WIDTH = 80;
    static final int HEIGHT = 20;

    static final double DT = 1.0 / 60.0;
    static final int PHYSICS_SUBSTEPS = 1024;
    static final double GRAVITY = -9.81;

    static double TIME_SCALE = 1.0;

    // -------------------------
    // Environment
    // -------------------------
    static boolean WATER_ON = false;
    static double WATER_LEVEL = HEIGHT / 2.0;

    static double AIR_DENSITY = 1.225;
    static double WATER_DENSITY = 1_000.0;
    static double AIR_VISCOSITY = 0.000018;
    static double WATER_VISCOSITY = 0.0091;

    // -------------------------
    // Object properties
    // -------------------------
    public static double x = 2.0, y = HEIGHT - 2.0;
    static double vx = 1_000.0, vy = -50.0;
    static double mass = 1.0;
    static double volume = 0.01;
    static double objectHeight = 1.0;
    static double crossSection = 0.01;

    // -------------------------
    // Material properties
    // -------------------------
    static double objectElasticity = 0.7;
    static double groundElasticity = 0.5;
    static double groundFriction = 0.7;
    static double objectFriction = 0.7;
    static double dragCoefficient = 1.0;

    public static void main(String[] args) throws InterruptedException {
        hideCursor();

        while (true) {
            long frameStart = System.nanoTime();

            updatePhysics();
            render();

            sleepFrame(frameStart);
        }
    }

    // public void start() {
    //     executor = Executors.newSingleThreadScheduledExecutor();
    //     executor.scheduleAtFixedRate(this::update, 0, (long) (1 / 60.0 * 1_000), TimeUnit.MILLISECONDS);
    // }
    public void update() {

    }

    // -------------------------
    // Physics update (substepped)
    // -------------------------
    static void updatePhysics() {
        double subDT = (DT * TIME_SCALE) / PHYSICS_SUBSTEPS;

        for (int i = 0; i < PHYSICS_SUBSTEPS; i++) {
            stepPhysics(subDT);
        }
    }

    static void stepPhysics(double dt) {
        boolean onGround = y <= 0;
        boolean inWater = WATER_ON && y <= WATER_LEVEL;

        double density = inWater ? WATER_DENSITY : AIR_DENSITY;
        double viscosity = inWater ? WATER_VISCOSITY : AIR_VISCOSITY;

        double ax = computeHorizontalAcceleration(onGround, density, viscosity);
        double ay = computeVerticalAcceleration(onGround, inWater, density, viscosity);

        vx += ax * dt;
        vy += ay * dt;

        x += vx * dt;
        y += vy * dt;

        // Floor
        if (y < 0) {
            y = 0;
            if (vy < 0) {
                double COR = Math.sqrt(objectElasticity * groundElasticity);
                vy = -vy * COR;
                vx *= (1 - groundFriction * objectFriction);
            } else {
                vy = 0;
            }
        }

        // Ceiling
        if (y >= HEIGHT - 1) {
            y = HEIGHT - 1;
            if (vy > 0) {
                vy = 0;
            }
        }

        // Walls
        if (x < 0) {
            x = 0;
            vx *= -0.5;
        }
        if (x > WIDTH - 1) {
            x = WIDTH - 1;
            vx *= -0.5;
        }
    }

    // -------------------------
    // Accelerations
    // -------------------------
    static double computeVerticalAcceleration(boolean onGround, boolean inWater, double density, double viscosity) {
        double ay = 0;
        if (!onGround) {
            ay += GRAVITY;
        }
        if (inWater) {
            ay += computeBuoyancy();
        }
        ay += computeDrag(vy, density, viscosity);
        return ay;
    }

    static double computeHorizontalAcceleration(boolean onGround, double density, double viscosity) {
        double ax = computeDrag(vx, density, viscosity);

        if (onGround) {
            double normalForce = mass * -GRAVITY;
            double frictionAcc = groundFriction * objectFriction * normalForce / mass;

            if (vx > 0) {
                ax = Math.max(ax - frictionAcc, -vx / DT);
            } else if (vx < 0) {
                ax = Math.min(ax + frictionAcc, -vx / DT);
            }
        }

        return ax;
    }

    // -------------------------
    // Buoyancy
    // -------------------------
    static double computeBuoyancy() {
        double submergedHeight = Math.clamp(WATER_LEVEL - y, 0.0, objectHeight);
        double submergedVolume = Math.clamp(volume * (submergedHeight / objectHeight), 0.0, volume);
        return -GRAVITY * WATER_DENSITY * submergedVolume / mass;
    }

    // -------------------------
    // Drag
    // -------------------------
    static double computeDrag(double v, double density, double viscosity) {
        double radius = Math.sqrt(crossSection / Math.PI);
        double linear = 6 * Math.PI * viscosity * radius / mass;
        double acc = -Math.signum(v) * linear * Math.abs(v);

        if (density <= AIR_DENSITY) {
            double k = 0.5 * density * dragCoefficient * crossSection / mass;
            acc += -Math.signum(v) * k * v * v;
        }

        return acc;
    }

    // -------------------------
    // Rendering
    // -------------------------
    static void render() {
        clearConsole();

        for (int r = HEIGHT - 1; r >= 0; r--) {
            StringBuilder line = new StringBuilder();
            for (int c = 0; c < WIDTH; c++) {
                if (r == (int) Math.round(y) && c == (int) Math.round(x)) {
                    if (WATER_ON && Math.round(y) <= WATER_LEVEL) {
                        line.append("\u001B[46m" + "O" + "\u001B[0m");
                    } else {
                        line.append("O");
                    }
                } else if (WATER_ON && r <= WATER_LEVEL) {
                    line.append("\u001B[46m" + "~" + "\u001B[0m");
                } else {
                    line.append(".");
                }
            }
            System.out.println(line);
        }

        System.out.printf(
                "position=(%.2f,%.2f) velocity=(%.2f,%.2f)%n",
                x, y, vx, vy
        );
    }

    // -------------------------
    // Utilities
    // -------------------------
    static void sleepFrame(long frameStart) throws InterruptedException {
        long frameTime = System.nanoTime() - frameStart;
        long sleepTime = (long) (DT * 1_000_000_000L) - frameTime;
        if (sleepTime > 0) {
            Thread.sleep(sleepTime / 1_000_000L);
        }
    }

    static void clearConsole() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }

    static void hideCursor() {
        System.out.print("\033[?25l");
    }
}
