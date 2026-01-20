
/**
 * @author Sinray Yang
 */
import java.awt.*;
import java.util.*;
import java.util.concurrent.locks.*;
import javax.swing.*;

class Flow extends JPanel implements Runnable {

    /* =========================================================
            ENGINE CONFIGS
        ========================================================= */
    static final class Simulation {

        static final Vector2D DIMENSIONS = new Vector2D(60.0, 40.0);
        static final double TICK_RATE = 60.0;
        static final double TICK_DURATION = 1.0 / TICK_RATE;
        static final long TICK_DURATION_NANOSECONDS = (long) (TICK_DURATION * 1e9);
        static final long SUBSTEPS_PER_TICK = 1;
        static double timeScale = 1.0;
    }

    static final class Physics {

        static final Vector2D GRAVITY = new Vector2D(0.0, -9.81);

        static final class Materials {

            static final class Air {

                static double density = 1.225;
                static double viscosity = 0.000018;
            }

            static final class Water {

                static double density = 1000.0;
                static double viscosity = 0.0091;
            }
        }

        static boolean useWater = false;
        static double waterLevel = Simulation.DIMENSIONS.y / 2.0;

        // Global surface properties
        static double boundElasticity = 0.7;
        static double boundStaticFriction = 2.0;
        static double boundDynamicFriction = 1.6;
    }

    static final class Render {
        // Preferred pixel canvas size

        static final int WIDTH = 800;
        static final int HEIGHT = 600;

        // Pixels per metre (dynamic scaling)
        static final double SCALE_X = WIDTH / Simulation.DIMENSIONS.x;
        static final double SCALE_Y = HEIGHT / Simulation.DIMENSIONS.y;
        static final double SCALE = Math.min(SCALE_X, SCALE_Y);
    }

    /* =========================================================
            ENGINE STATE
        ========================================================= */
    public final ArrayList<Ball> queuedBalls = new ArrayList<>();
    private final ArrayList<Ball> activeBalls = new ArrayList<>();

    private volatile boolean running = true;
    private Thread engineThread = null;

    /* =========================================================
            INITIALIZATION
        ========================================================= */
    public Flow() {
        Dimension dimension = new Dimension(Render.WIDTH, Render.HEIGHT);
        setPreferredSize(dimension);
        setBackground(Color.BLACK);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Image iconImage = new ImageIcon(Flow.class.getResource("icon.png")).getImage();
            Flow engine = new Flow();
            JFrame frame = new JFrame("Flow");

            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setIconImage(iconImage);
            frame.setContentPane(engine);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);

            engine.start();
        });
    }

    public void start() {
        Random rand = new Random();

        for (int i = 0; i < 800; i++) {
            double x = lerp(Simulation.DIMENSIONS.x / 2.0 - 5.0, Simulation.DIMENSIONS.x / 2.0 + 5.0, rand.nextDouble());
            double y = lerp(Simulation.DIMENSIONS.y - 10.0, Simulation.DIMENSIONS.y - 5.0, rand.nextDouble());

            double vx = lerp(-500.0, 500.0, rand.nextDouble());
            double vy = lerp(-50.0, 50.0, rand.nextDouble());

            double mass = lerp(500.0, 2_000.0, rand.nextDouble());
            double radius = lerp(0.4, 0.8, rand.nextDouble());

            Color colour = Color.getHSBColor(rand.nextFloat(), 1f, 1f);

            synchronized (queuedBalls) {
                queuedBalls.add(new Ball(x, y, vx, vy, mass, radius, colour));
            }
        }

        engineThread = new Thread(this, "engine");
        engineThread.setDaemon(true);
        engineThread.start();
    }

    public void quit() {
        running = false;
        if (engineThread != null) engineThread.interrupt();
        queuedBalls.clear();
        activeBalls.clear();
    }

    /* =========================================================
            ENGINE LOOP
        ========================================================= */
    @Override
    public void run() {
        long nextTick = System.nanoTime();
        while (running) {
            long currentTick = System.nanoTime();
            int steps = 0;
            while (currentTick >= nextTick && steps < 5) {
                stepPhysics();
                nextTick += Simulation.TICK_DURATION_NANOSECONDS;
                steps++;
            }
            SwingUtilities.invokeLater(this::repaint);

            long sleepTime = nextTick - System.nanoTime();
            if (sleepTime > 0) {
                LockSupport.parkNanos(sleepTime);
            }
        }
    }

    /* =========================================================
            PHYSICS STEP
        ========================================================= */
    private void stepPhysics() {
        double dt = Simulation.timeScale * Simulation.TICK_DURATION / Simulation.SUBSTEPS_PER_TICK;

        synchronized (queuedBalls) {
            activeBalls.addAll(queuedBalls);
            queuedBalls.clear();
        }

        for (int step = 0; step < Simulation.SUBSTEPS_PER_TICK; step++) {
            synchronized (queuedBalls) {
                for (Ball b : activeBalls) {
                    b.integrateVelocity(dt);
                }
                for (Ball b : activeBalls) {
                    b.predictPosition(dt);
                }

                for (int iter = 0; iter < 4; iter++) {
                    for (Ball b : activeBalls) {
                        b.solveBoundaryConstraints();
                    }
                    resolveBallCollisions();
                }

                for (Ball b : activeBalls) {
                    b.commitPosition();
                }
            }
        }
    }

    private void resolveBallCollisions() {
        synchronized (queuedBalls) {
            int size = activeBalls.size();

            for (int i = 0; i < size; i++) {
                Ball a = activeBalls.get(i);

                for (int j = i + 1; j < size; j++) {
                    Ball b = activeBalls.get(j);

                    double dx = b.px - a.px;
                    double dy = b.py - a.py;
                    double distSq = dx * dx + dy * dy;
                    double radiiSum = a.radius + b.radius;

                    if (distSq < radiiSum * radiiSum && distSq > 0) {
                        double dist = Math.sqrt(distSq);
                        double overlap = radiiSum - dist;
                        double nx = dx / dist;
                        double ny = dy / dist;
                        double totalMass = a.mass + b.mass;

                        // Push apart
                        a.px -= nx * overlap * (b.mass / totalMass);
                        a.py -= ny * overlap * (b.mass / totalMass);
                        b.px += nx * overlap * (a.mass / totalMass);
                        b.py += ny * overlap * (a.mass / totalMass);

                        // Relative velocity
                        double relVx = b.vx - a.vx;
                        double relVy = b.vy - a.vy;
                        double relVelAlongNormal = relVx * nx + relVy * ny;

                        if (relVelAlongNormal > 0) {
                            continue;
                        }

                        double e = Math.min(a.objectElasticity, b.objectElasticity);
                        double invMassSum = 1.0 / a.mass + 1.0 / b.mass;
                        double jn = -(1 + e) * relVelAlongNormal / invMassSum;

                        double fx = jn * nx;
                        double fy = jn * ny;

                        a.vx -= fx / a.mass;
                        a.vy -= fy / a.mass;
                        b.vx += fx / b.mass;
                        b.vy += fy / b.mass;

                        // --- STATIC + DYNAMIC FRICTION ---
                        double tx = -ny;
                        double ty = nx;
                        double vt = relVx * tx + relVy * ty;

                        double muStatic = Math.min(a.objectStaticFriction(), b.objectStaticFriction());
                        double muDynamic = Math.min(a.objectDynamicFriction(), b.objectDynamicFriction());

                        double jtDesired = -vt / invMassSum;
                        double jtMaxStatic = muStatic * jn;

                        double jt;
                        if (Math.abs(jtDesired) <= jtMaxStatic) {
                            jt = jtDesired; // static friction applies fully
                        } else {
                            jt = Math.signum(jtDesired) * muDynamic * jn; // dynamic friction
                        }

                        double fxT = jt * tx;
                        double fyT = jt * ty;

                        a.vx -= fxT / a.mass;
                        a.vy -= fyT / a.mass;
                        b.vx += fxT / b.mass;
                        b.vy += fyT / b.mass;
                    }
                }
            }
        }
    }

    /* =========================================================
            BALL
        ========================================================= */
    final class Ball {

        double x, y, vx, vy, px, py;
        double mass, radius;
        double objectElasticity = 0.9;
        double objectStaticFriction = 1.6;
        double objectDynamicFriction = 0.9;
        final Color colour;

        Ball(double x, double y, double vx, double vy, double mass, double radius, Color colour) {
            this.x = this.px = x;
            this.y = this.py = y;
            this.vx = vx;
            this.vy = vy;
            this.mass = mass;
            this.radius = radius;
            this.colour = colour;
        }

        void integrateVelocity(double dt) {
            vx += computeHorizontalAcceleration() * dt;
            vy += computeVerticalAcceleration() * dt;
        }

        void predictPosition(double dt) {
            px = x + vx * dt;
            py = y + vy * dt;
        }

        void commitPosition() {
            x = px;
            y = py;
        }

        void solveBoundaryConstraints() {
            double dt = Simulation.TICK_DURATION / Simulation.SUBSTEPS_PER_TICK;

            // -------------------
            // Bottom wall
            // -------------------
            if (py - radius < 0) {
                py = radius;
                if (vy < 0) {
                    vy = -vy * objectElasticity * Physics.boundElasticity;
                }

                applyFriction(dt, Math.abs(Physics.GRAVITY.y), true);
            }

            // -------------------
            // Top wall
            // -------------------
            if (py + radius > Simulation.DIMENSIONS.y) {
                py = Simulation.DIMENSIONS.y - radius;
                if (vy > 0) {
                    vy = -vy * objectElasticity * Physics.boundElasticity;
                }

                applyFriction(dt, Math.abs(Physics.GRAVITY.y), true);
            }

            // -------------------
            // Left wall
            // -------------------
            if (px - radius < 0) {
                px = radius;
                if (vx < 0) {
                    vx = -vx * objectElasticity * Physics.boundElasticity;
                }

                applyFriction(dt, Math.abs(Physics.GRAVITY.x), false);
            }

            // -------------------
            // Right wall
            // -------------------
            if (px + radius > Simulation.DIMENSIONS.x) {
                px = Simulation.DIMENSIONS.x - radius;
                if (vx > 0) {
                    vx = -vx * objectElasticity * Physics.boundElasticity;
                }

                applyFriction(dt, Math.abs(Physics.GRAVITY.x), false);
            }
        }

        private void applyFriction(double dt, double normalForce, boolean horizontal) {
            double muS = objectStaticFriction * Physics.boundStaticFriction;
            double muD = objectDynamicFriction * Physics.boundDynamicFriction;

            double maxStaticAccel = muS * normalForce / mass;
            double maxDynamicAccel = muD * normalForce / mass;

            if (horizontal) {
                if (Math.abs(vx) < maxStaticAccel * dt) {
                    vx = 0;
                } else {
                    vx -= Math.signum(vx) * maxDynamicAccel * dt;
                }
            } else {
                if (Math.abs(vy) < maxStaticAccel * dt) {
                    vy = 0;
                } else {
                    vy -= Math.signum(vy) * maxDynamicAccel * dt;
                }
            }
        }

        double computeVerticalAcceleration() {
            double ay = Physics.GRAVITY.y;  // gravity always applies downwards

            double airRho = Physics.Materials.Air.density;
            double waterRho = Physics.Materials.Water.density;
            double airMu = Physics.Materials.Air.viscosity;
            double waterMu = Physics.Materials.Water.viscosity;

            if (Physics.useWater) {
                double submergedArea = getSubmergedArea();
                double submergedLength = getSubmergedProjectedLength();
                double airLength = getProjectedLength() - submergedLength;

                // Buoyancy always points upward
                if (submergedArea > 0) {
                    double buoyancyAccel = waterRho * submergedArea / mass * Math.abs(Physics.GRAVITY.y);
                    ay += buoyancyAccel;  // ADD upward, always
                }

                // Drag in air
                if (airLength > 0) {
                    ay += drag(vy, airRho, airMu, airLength);
                }

                // Drag in water
                if (submergedLength > 0) {
                    ay += drag(vy, waterRho, waterMu, submergedLength);
                }

            } else {
                ay += drag(vy, airRho, airMu, getProjectedLength());
            }

            return ay;
        }

        double computeHorizontalAcceleration() {
            double ax = Physics.GRAVITY.x;
            double airRho = Physics.Materials.Air.density;
            double waterRho = Physics.Materials.Water.density;
            double airMu = Physics.Materials.Air.viscosity;
            double waterMu = Physics.Materials.Water.viscosity;

            double submergedLength = Math.max(0, Math.min(getSubmergedProjectedLength(), getProjectedLength()));
            double airLength = getProjectedLength() - submergedLength;

            if (Physics.useWater) {
                if (airLength > 0) {
                    ax += drag(vx, airRho, airMu, airLength);
                }
                if (submergedLength > 0) {
                    ax += drag(vx, waterRho, waterMu, submergedLength);
                }
            } else {
                ax += drag(vx, airRho, airMu, getProjectedLength());
            }

            return ax;
        }

        // --- Geometry helpers ---
        double getArea() {
            return Math.PI * radius * radius;
        }

        double getProjectedLength() {
            return 2.0 * radius;
        }

        double getSubmergedArea() {
            double r = radius, bottom = y - r, h = Physics.waterLevel - bottom;
            if (h <= 0) {
                return 0;
            }
            if (h >= 2 * r) {
                return getArea();
            }
            double cosAngle = Math.clamp((r - h) / r, -1, 1);
            double theta = Math.acos(cosAngle);
            return r * r * theta - (r - h) * Math.sqrt(Math.max(0, 2 * r * h - h * h));
        }

        double getSubmergedProjectedLength() {
            double r = radius, bottom = y - r, h = Physics.waterLevel - bottom;
            if (h <= 0) {
                return 0;
            }
            if (h >= 2 * r) {
                return getProjectedLength();
            }
            double yLocal = h - r, halfWidth = Math.sqrt(r * r - yLocal * yLocal);
            return 2.0 * halfWidth;
        }

        private double drag(double v, double density, double viscosity, double projectedLength) {
            if (v == 0) {
                return 0;
            }
            double speed = Math.abs(v), L = 2.0 * radius;
            double Re = Math.max(density * speed * L / viscosity, 1e-6);
            double Cd = Re < 1 ? 24 / Re : Re < 400 ? 24 / Re * (1 + 0.15 * Math.pow(Re, 0.687)) : Re < 2e5 ? 1.0 : 0.3;
            double linear = 6.0 * Math.PI * viscosity * radius / mass;
            double quad = 0.5 * density * Cd * projectedLength / mass;
            return -linear * v - Math.signum(v) * quad * speed * speed;
        }

        double objectStaticFriction() {
            return objectStaticFriction;
        }

        double objectDynamicFriction() {
            return objectDynamicFriction;
        }
    }

    /* =========================================================
            RENDERING
        ========================================================= */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;

        // Centre the simulation in the panel
        double simWidthPx = Simulation.DIMENSIONS.x * Render.SCALE;
        double simHeightPx = Simulation.DIMENSIONS.y * Render.SCALE;
        double offsetX = (getWidth() - simWidthPx) / 2.0;
        double offsetY = (getHeight() - simHeightPx) / 2.0;

        // World-to-screen helpers
        java.util.function.DoubleUnaryOperator wx = wxVal -> offsetX + wxVal * Render.SCALE;
        java.util.function.DoubleUnaryOperator wy = wyVal -> offsetY + (Simulation.DIMENSIONS.y - wyVal) * Render.SCALE;

        // Fill entire outside area with wall colour first
        g2.setColor(Color.DARK_GRAY);
        g2.fillRect(0, 0, getWidth(), getHeight());

        int wallThickness = 10;

        // Draw simulation background (inside walls)
        g2.setColor(Color.BLACK);
        g2.fillRect((int) offsetX, (int) offsetY, (int) simWidthPx, (int) simHeightPx);

        // Draw water inside simulation if enabled
        if (Physics.useWater) {
            g2.setColor(new Color(0, 150, 200, 120));
            int waterTop = (int) wy.applyAsDouble(Physics.waterLevel);
            g2.fillRect((int) offsetX, waterTop, (int) simWidthPx, (int) (offsetY + simHeightPx - waterTop));
        }

        // Draw solid walls along simulation edges (outer walls)
        g2.setColor(Color.DARK_GRAY);
        // Bottom
        g2.fillRect(
                (int) offsetX - wallThickness,
                (int) (offsetY + simHeightPx),
                (int) (simWidthPx + 2 * wallThickness),
                wallThickness
        );
        // Top
        g2.fillRect(
                (int) offsetX - wallThickness,
                (int) offsetY - wallThickness,
                (int) (simWidthPx + 2 * wallThickness),
                wallThickness
        );
        // Left
        g2.fillRect(
                (int) offsetX - wallThickness,
                (int) offsetY - wallThickness,
                wallThickness,
                (int) (simHeightPx + 2 * wallThickness)
        );
        // Right
        g2.fillRect(
                (int) (offsetX + simWidthPx),
                (int) offsetY - wallThickness,
                wallThickness,
                (int) (simHeightPx + 2 * wallThickness)
        );

        // Draw balls
        synchronized (queuedBalls) {
            for (Ball b : activeBalls) {
                int r = (int) (b.radius * Render.SCALE);
                int sx = (int) wx.applyAsDouble(b.x) - r;
                int sy = (int) wy.applyAsDouble(b.y) - r;
                g2.setColor(b.colour);
                g2.fillOval(sx, sy, r * 2, r * 2);
            }
        }
    }


    /* =========================================================
            RENDERING
        ========================================================= */
    static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
