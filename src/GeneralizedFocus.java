import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;

import java.io.File;
import java.util.*;

public class GeneralizedFocus {
    /**
     * List of images to merge together
     */
    private ArrayList<Mat> inputs = new ArrayList<>();
    private ArrayList<Mat> lowRes = new ArrayList<>();
    private int numberOfInputs;
    private ArrayList<Double[][]> focusMeasureMaps = new ArrayList<>();
    private ArrayList<Integer[][]> coveringSet = new ArrayList<>();
    private List<Integer> selectedImages = new ArrayList<>();
    private int nRows;
    private int nCols;
    private int[][] focusSelectionMap;
    private double[][] focusSelectionMem;
    private ArrayList<Mat> horizontals = new ArrayList<>();
    private ArrayList<Mat> verticals = new ArrayList<>();
    private Mat output;
    private double thrashHold;
    private double thrashHold2;
    private final PrettyPrinter printer = new PrettyPrinter(System.out);

    /**
     * Path to the folder which contains the images
     */
    private String inputPath;
    private String outputPath;


    GeneralizedFocus(String inputPath, String outputPath, int nRows, int nCols, double backgroundThrashHold, double setCoverThrashHold) {
        this.nRows = nRows;
        this.nCols = nCols;
        this.inputPath = inputPath.replace("\\", "/");
        this.outputPath = outputPath.replace("\\", "/");
        this.thrashHold = backgroundThrashHold;
        this.thrashHold2 = setCoverThrashHold;
    }

    /**
     * Focus measure for a matrix as descriebed in
     * D. Choi et al., Improved Image Selection for Focus Stacking (equation 1)
     */
    private double focusMeasure(Mat subMatrix) {
        double focusMeasure = 0;
        for (int r = 0; r < subMatrix.rows(); r++)
            for (int c = 1; c < subMatrix.cols() - 1; c++) {
                focusMeasure += Math.abs(-subMatrix.get(r, c - 1)[0] +
                        2 * subMatrix.get(r, c)[0] -
                        subMatrix.get(r, c + 1)[0]);
            }
        return focusMeasure / subMatrix.cols() * subMatrix.rows();
    }


    /**
     * As focusMeasure but instead of just looking at the horizontal neighbors also consider the vertical neighbors
     * The focusMeasure described  by David Choi can lead to wo bad measure for areas with objects with horizontal edges
     * This also costs processing time.
     */
    private double focusMeasureImproved(Mat subMatrix) {
        double focusMeasure = 0;
        for (int r = 1; r < subMatrix.rows() - 1; r++)
            for (int c = 1; c < subMatrix.cols() - 1; c++) {
                focusMeasure += Math.abs(-subMatrix.get(r, c - 1)[0] +
                        2 * subMatrix.get(r, c)[0] -
                        subMatrix.get(r, c + 1)[0])
                        + Math.abs(-subMatrix.get(r - 1, c)[0] +
                        2 * subMatrix.get(r, c)[0] -
                        subMatrix.get(r + 1, c)[0]);
            }
        return focusMeasure / subMatrix.cols() * subMatrix.rows();
    }

    /**
     * apply generalFocus on inputs
     */
    void generalFocus() {
        for (int i = 0; i < lowRes.size(); i++) {
            selectedImages.add(i);
        }
        setFocusMeasureMapAndMem(selectedImages);

        //generate coveringSet
        for (int r = 0; r < focusMeasureMaps.get(0).length; r++) {
            for (int c = 0; c < focusMeasureMaps.get(0)[r].length; c++) {
                for (int i = 0; i < focusMeasureMaps.size(); i++) {
                    if (Math.abs(focusMeasureMaps.get(i)[r][c] - focusSelectionMem[r][c]) <= thrashHold2)
                        coveringSet.get(i)[r][c] = 1;
                    else coveringSet.get(i)[r][c] = 0;
                }
            }
        }

        List<Set<Integer>> coveringSets = new ArrayList<>();
        for (Integer[][] aCoveringSet : coveringSet) {
            Set<Integer> temp = new HashSet<>();
            for (int r = 0; r < aCoveringSet.length; r++) {
                for (int c = 0; c < aCoveringSet[r].length; c++) {
                    if (aCoveringSet[r][c] == 1)
                        temp.add(r * nCols + c);
                }
            }
            coveringSets.add(temp);
        }
        Set<Integer> foreGround = determineBackgroundAndHomogeneousCells();
        System.out.printf("Images contains %d out of %d foreGround cells " +
                "\n(change the thresholds to get different results)\n" ,foreGround.size(), nRows*nCols);
        reduceSelectionMap(coveringSets, foreGround);

        System.out.print("Images from stack used ");
        selectedImages.forEach(e -> System.out.printf("%d ", e));


//        for (int i = 0; i < coveringSet.size(); i++) {
//            System.out.printf("%d", i);
//            printer.print(coveringSet.get(i));
//        }

        for (int i = 0; i < numberOfInputs; i++) {
            if (!selectedImages.contains(i)) {
                inputs.remove(i);
                lowRes.remove(i);
            }
        }

        System.out.printf("\nUsed %d images from %d\n", inputs.size(), numberOfInputs);
        setFocusMeasureMapAndMem(selectedImages);


        int rowStart = 0;
        int colStart;
        int rowDif = inputs.get(0).rows() / nRows;
        int colDif = inputs.get(0).cols() / nCols;
        int i;
        for (int[] aFocusSelectionMap : focusSelectionMap) {
            colStart = 0;
            i = 0;
            for (int anAFocusSelectionMap : aFocusSelectionMap) {
                if (horizontals.size() <= i) horizontals.add(new Mat());
                horizontals.set(i, inputs.get(anAFocusSelectionMap).submat(rowStart, rowStart + rowDif, colStart, colStart + colDif));
                i++;
                colStart += colDif;
            }
            Core.hconcat(horizontals, output);
            verticals.add(output.clone());
            rowStart += rowDif;
        }
        Core.vconcat(verticals, output);
        Imgcodecs.imwrite(outputPath + "(generalized).jpg", output);
    }


    /**
     * Sets the cells in the focusSelectionMap map to 0 when a cell is background or homogeneous cell
     * A cell is considered background or homogeneous cell if the standard deviation of all corresponding cells in the
     * stack is below a given threshold
     * if useMostFrequent is set to true the function determines which image is used for most cells and uses this image
     * also for the background cells to reduce the number of artifacts which can occur when merging different focuses
     * together
     * if useMostFrequent is set to false the background cells use image 0 because in the previous alignment process all
     * the images are aligned to the image 0 there for there wont be any artifacts from merging different images in
     * the Background and homogeneous areas.
     */
    private Set<Integer> determineBackgroundAndHomogeneousCells() {
        Set<Integer> foreGround = new HashSet<>();
        double standard;
        double biggest;
        int backgroundImage = 0;
        int[] counts = new int[lowRes.size()];
        for (int[] aFocusSelectionMap : focusSelectionMap) {
            for (int anAFocusSelectionMap : aFocusSelectionMap) {
                counts[anAFocusSelectionMap]++;
            }
        }
        int max = 0;
        for (int i = 0; i != lowRes.size(); i++) {
            if (counts[i] > max) {
                max = counts[i];
                backgroundImage = i;
            }
        }
        for (int r = 0; r < nRows; r++) {
            for (int c = 0; c < nCols; c++) {
                standard = 0;
                biggest = 0;
                for (int i = 0; i < lowRes.size(); i++) {
                    standard += focusMeasureMaps.get(i)[r][c];
                }
                standard /= lowRes.size();
                for (int i = 0; i < lowRes.size(); i++) {
                    if (biggest < Math.abs(focusMeasureMaps.get(i)[r][c] - standard)) {
                        biggest = Math.abs(focusMeasureMaps.get(i)[r][c] - standard);
                    }
                }
                if (biggest < thrashHold) {
                    focusSelectionMap[r][c] = backgroundImage;
                } else {
                    foreGround.add(r * nCols + c);
                }
            }
        }
        return foreGround;
    }


    /**
     * Fill inputs list using the path
     */
    void fill() {
        File directory = new File(inputPath);

        if (!directory.exists()) {
            System.out.println("directory : " + inputPath + " doesn't exist");
        } else {
            File[] files = directory.listFiles();

            assert files != null;
            for (File file : files) {
                String nom = file.getName();
                inputs.add(Imgcodecs.imread(inputPath + nom));
                lowRes.add(Imgcodecs.imread(inputPath + nom, Imgcodecs.IMREAD_REDUCED_GRAYSCALE_8));
                numberOfInputs = inputs.size();
                output = new Mat(inputs.get(0).rows(), inputs.get(0).cols(), inputs.get(0).type());
            }
        }
    }


    /**
     * Fills the FocusMeasureMap and the FocusMeasureMem(which contains the FocusMeasures for the corresponding map)
     * using the metric described by David Choi in "IMPROVED IMAGE SELECTION FOR FOCUS STACKING IN DIGITAL PHOTOGRAPHY"
     */
    private void setFocusMeasureMapAndMem(List<Integer> images) {
        this.focusSelectionMap = new int[nRows][nCols];
        this.focusSelectionMem = new double[nRows][nCols];
        focusMeasureMaps = new ArrayList<>();
        for (int i = 0; i < lowRes.size(); i++) {
            focusMeasureMaps.add(new Double[nRows][nCols]);
            coveringSet.add(new Integer[nRows][nCols]);
            int rowStart = 0;
            int colStart = 0;
            int rowDif = lowRes.get(i).rows() / nRows;
            int colDif = lowRes.get(i).cols() / nCols;
            double[][] focusMeasures = new double[nRows][nCols];
            for (int r = 0; r < focusMeasures.length; r++) {
                colStart = 0;
                for (int c = 0; c < focusMeasures[r].length; c++) {
                    focusMeasureMaps.get(i)[r][c] = focusMeasureImproved(lowRes.get(i).submat(rowStart, rowStart + rowDif, colStart, colStart + colDif));
                    colStart += colDif;
                }
                rowStart += rowDif;
            }
        }

        for (int i = 0; i < focusMeasureMaps.size(); i++) {
            for (int r = 0; r < focusMeasureMaps.get(i).length; r++) {
                for (int c = 0; c < focusMeasureMaps.get(i)[r].length; c++) {
                    if (focusSelectionMem[r][c] < focusMeasureMaps.get(i)[r][c]) {
                        focusSelectionMem[r][c] = focusMeasureMaps.get(i)[r][c];
                        focusSelectionMap[r][c] = i;
                    }
                }
            }
        }
    }


    /**
     * Algorithm to reduce the Stack of actually used images described by Daniel Vaquero in Generalized Autofocus
     * Considering that there is an order between the sets and for a given cell its sharpness varies as a function of
     * the images in the stack. This function is uni modal and therefore it becomes a box function once it is
     * thresholded
     * exploiting this additional information we can solve the usualy NP-complete setCovering in linear time
     **/
    private void reduceSelectionMap(List<Set<Integer>> coveringSets, Set<Integer> foreGround) {
        for (int i = 0; i < coveringSets.size(); i++) {
            coveringSets.set(i, intersection(coveringSets.get(i), foreGround));
        }
        coveringSets.add(new HashSet<>());
        Set<Integer> active = new HashSet<>();
        Set<Integer> processed = new HashSet<>();
        List<Integer> selectedImages = new ArrayList<>();
        for (int i = 1; i < coveringSets.size(); i++) {
            Set<Integer> sharp = difference(coveringSets.get(i - 1), processed);
            Set<Integer> f0 = difference(active, sharp);
            Set<Integer> f1 = difference(sharp, active);
            if (!f0.isEmpty()) {
                selectedImages.add(i - 1);
            }
            if (!f1.isEmpty()) {
                active = union(active, f1);
            }
        }
        this.selectedImages = selectedImages;
    }


    /**
     * HelperFunction since Java Set doesn't implement union
     */
    private static <T> Set<T> union(Set<T> setA, Set<T> setB) {
        Set<T> tmp = new TreeSet<T>(setA);
        tmp.addAll(setB);
        return tmp;
    }

    /**
     * HelperFunction since Java Set doesn't implement intersection
     */
    private static <T> Set<T> intersection(Set<T> setA, Set<T> setB) {
        Set<T> tmp = new TreeSet<T>();
        for (T x : setA)
            if (setB.contains(x))
                tmp.add(x);
        return tmp;
    }

    /**
     * HelperFunction since Java Set doesn't implement difference
     */
    private static <T> Set<T> difference(Set<T> setA, Set<T> setB) {
        Set<T> tmp = new TreeSet<T>(setA);
        tmp.removeAll(setB);
        return tmp;
    }
}