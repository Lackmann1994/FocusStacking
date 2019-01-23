import org.opencv.core.Core;

import java.io.File;


public class Launcher {

    private static int nRows = 16;
    private static int nCols = 24;
    // The ThrashHold can change The result and highly depends on the Image;
    //(Especially the amount of images used for the Stack and Background determination)
    private static double setCoverThrashHold = 30;
    private static double backgroundThrashHold = setCoverThrashHold*3;
    public static void main(String[] args) {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
//        generateResult(args[0]);
        generateAllResults();
    }

    static private void generateResult(String name) {
        String input = System.getProperty("user.dir") + "\\Images\\aligned\\" + name + "\\";
        String output = System.getProperty("user.dir") + "\\Images\\results(prealigned)\\" + name;
//        FocusStacking focusStacking = new FocusStacking(input, output);
//        focusStacking.fill();
//        focusStacking.focusStack();

        GeneralizedFocus generalizedFocus = new GeneralizedFocus(input, output, nRows, nCols, backgroundThrashHold, setCoverThrashHold);
        generalizedFocus.fill();
        generalizedFocus.generalFocus();
    }

    static private void generateAllResults() {
        File directory = new File(System.getProperty("user.dir") + "\\Images\\aligned\\");
        if (!directory.exists()) {
            System.out.println("directory : " + System.getProperty("user.dir") + "\\Images\\aligned\\ doesn't exist");
        } else {
            File[] files = directory.listFiles();
            assert files != null;
            for (File file : files) {

                System.out.println(file.toString().replace("aligned", "results(prealigned)"));

//                FocusStacking focusStacking = new FocusStacking(file + "\\", file.toString().replace("aligned", "results(prealigned)"));
//                focusStacking.fill();
//                focusStacking.focusStack();

                GeneralizedFocus generalizedFocus = new GeneralizedFocus(file + "\\", file.toString().replace("aligned", "results(prealigned)"), nRows, nCols, backgroundThrashHold, setCoverThrashHold);
                generalizedFocus.fill();
                generalizedFocus.generalFocus();
            }
        }
    }
}




























