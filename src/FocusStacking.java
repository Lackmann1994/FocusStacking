import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.util.ArrayList;

import static javax.swing.text.StyleConstants.Size;

public class FocusStacking {
    /**
     * List of images to merge together
     */
    private ArrayList<Mat> inputs = new ArrayList<Mat>();

    /**
     * Path to the folder which contains the images
     */
    private String inputPath;
    private String outputPath;

    public FocusStacking(String inputPath, String outputPath) {
        this.inputPath = inputPath.replace("\\", "/");
        this.outputPath = outputPath.replace("\\", "/");
    }

    public FocusStacking(String inputPath, String outputPath, int lapSize) {
        this.inputPath = inputPath.replace("\\", "/");
        this.outputPath = outputPath.replace("\\", "/");
    }

    /**
     * Compute the gradient map of the image
     *
     * @param image image to transform
     * @return image image transformed
     */
    public Mat laplacien(Mat image) {
        int kernel_size = 5;
        double blur_size = 5;

        Mat gray = new Mat();
        Imgproc.cvtColor(image, gray, Imgproc.COLOR_BGR2GRAY);

        Mat gauss = new Mat();
        Imgproc.GaussianBlur(gray, gauss, new Size(blur_size, blur_size), 0);

        Mat laplace = new Mat();
        Imgproc.Laplacian(gauss, laplace, gauss.type(), kernel_size, 1, 0);

        Mat absolute = new Mat();
        Core.convertScaleAbs(laplace, absolute);

        return absolute;
    }

    /**
     * apply focus stacking on inputs
     */
    void focusStack() {
        if (inputs.size() == 0) {
            System.out.println("please select some inputs");
        } else {
            System.out.println("Computing the laplacian of the blurred images");
            Mat[] laps = new Mat[inputs.size()];

            for (int i = 0; i < inputs.size(); i++) {
                System.out.println("image " + i);
                laps[i] = laplacien(inputs.get(i));
            }

            Mat vide = Mat.zeros(laps[0].size(), inputs.get(0).type());

            for (int y = 0; y < laps[0].cols(); y++) {
                for (int x = 0; x < laps[0].rows(); x++) {
                    int index = -1;
                    double indexValue = -1;
                    for (int i = 0; i < laps.length; i++) {
                        if (indexValue == -1 || laps[i].get(x, y)[0] > indexValue) {
                            indexValue = laps[i].get(x, y)[0];
                            index = i;
                        }
                    }
                    vide.put(x, y, inputs.get(index).get(x, y));
                }
            }
            System.out.println("Success!");

            Imgcodecs.imwrite(outputPath + "(FocusStacking).jpg", vide);
        }
    }


    /**
     * Fill inputs list using the inputPath
     */
    public void fill() {
        File repertoire = new File(inputPath);

        if (!repertoire.exists()) {
            System.out.println("directory : " + inputPath + " doesn't exist");
        } else {
            File[] files = repertoire.listFiles();

            for (int i = 0; i < files.length; i++) {
                String nom = files[i].getName();
                inputs.add(Imgcodecs.imread(inputPath + nom));
                System.out.println(Imgcodecs.imread(inputPath + nom).size());
            }
        }
    }
}