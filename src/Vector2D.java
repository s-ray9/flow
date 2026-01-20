/**
 * @author Sinray Yang
 */

import java.awt.*;

public class Vector2D {
    public double x, y;

    // Default constructor
    public Vector2D() {
        this.x = this.y = 0.0;
    }

    // Constructor with x and y
    public Vector2D(double x, double y) {
        this.x = x;
        this.y = y;
    }

    // Squared magnitude
    public double magnitudeSquared() {
        return this.x * this.x + this.y * this.y;
    }

    // Magnitude
    public double magnitude() {
        return Math.sqrt(this.magnitudeSquared());
    }

    // Add another vector
    public Vector2D add(Vector2D other) {
        return new Vector2D(x + other.x, y + other.y);
    }

    // Subtract another vector
    public Vector2D subtract(Vector2D other) {
        return new Vector2D(x - other.x, y - other.y);
    }

    // Element-wise multiplication
    public Vector2D multiply(Vector2D other) {
        return new Vector2D(x * other.x, y * other.y);
    }

    // Multiply by a scalar
    public Vector2D multiply(double factor) {
        return new Vector2D(x * factor, y * factor);
    }

    // Element-wise division
    public Vector2D divide(Vector2D other) {
        return new Vector2D(x / other.x, y / other.y);
    }

    // Divide by a scalar
    public Vector2D divide(double divisor) {
        return new Vector2D(x / divisor, y / divisor);
    }

    // Unit vector (normalized)
    public Vector2D unit() {
        return divide(magnitude());
    }

    // Dot product with another vector
    public double dot(Vector2D other) {
        return this.x * other.x + this.y * other.y;
    }

    // Convert to Dimension for use with Java's GUI components
    public Dimension toDimension() {
        return new Dimension((int) x, (int) y);
    }
}