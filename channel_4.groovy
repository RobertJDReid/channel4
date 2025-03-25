// ***************************************************************************************
// *
// *                                      Channel 4
// *
// *                                    Version 0.11
// *
// ***************************************************************************************

import ij.IJ
import ij.WindowManager as WM
import ij.text.TextWindow
import ij.gui.GenericDialog
import ij.plugin.ZProjector
import ij.process.ImageStatistics
import ij.process.ImageProcessor
import ij.process.ImageConverter
import ij.Prefs
import java.awt.Checkbox
import java.awt.Panel
import java.awt.ScrollPane
import java.awt.GridLayout
import java.awt.BorderLayout


// Use script params to take care of UI

#@ File (label="choose input file", style="file") pathToFile
#@ File (label="choose folder with PSF files", style="directory") psfPath
#@ File (label="choose output folder", style="directory") outputDir
#@ Integer (label="Enter number of deconvolution interations", style="slider", min=1, max=15, stepSize=1) deconIter
#@ String (label="Binary threshhold",choices={"MaxEntropy", "Otsu", "Huang", "Mean", "Default"}, style="listBox") cfpBinThrMethod
#@ Boolean (label="Monitor deconvolution") deconMonitor

def redChannel = 1; // Red channel number - will parameterize at some point
def yellowChannel = 2; // Yellow channel number - will parameterize at some point
def blueChannel = 3; // Blue channel number - will parameterize at some point

println("output dir is : " + outputDir)

// -------------------------------------------------------------------------
// Step 1) Run the Bio-Formats Importer in "Metadata only" mode
// (Adjust your path as needed)
//String pathToFile = "/Volumes/Micro_Data/Rachel /1-28-25 D4 Glyc Lac C39-46/1-28-25 D4 Glyc Lac C39-46.mvd2"
//IJ.log("File selected: " + pathToFile)

IJ.run("Bio-Formats Importer",
       "open=[" + pathToFile + "] " +
       "color_mode=Default " +
       "display_metadata " +
       "rois_import=[ROI manager] " +
       "view=[Metadata only] " +
       "stack_order=Default");

// -------------------------------------------------------------------------
// Step 2) Grab the newly created text window from the WM
String metadataWindowTitle = null
String[] nonImageTitles = WM.getNonImageTitles()
for (String t in nonImageTitles) {
    if (t.startsWith("Original Metadata")) {
        metadataWindowTitle = t
        break
    }
}

if (metadataWindowTitle == null) {
    IJ.log("Could not find the Bio-Formats metadata window!")
    return
}

def window = WM.getWindow(metadataWindowTitle)
if (!(window instanceof TextWindow)) {
    IJ.log("Found window titled '${metadataWindowTitle}' but it's not a TextWindow.")
    return
}

// -------------------------------------------------------------------------
// Step 3) Extract the text and (optionally) close the metadata window
String metadataText = window.getTextPanel().getText()
IJ.log("=== Captured Metadata ===\n" + metadataText)
//window.close()  // close it if you don't need that window open

// -------------------------------------------------------------------------
// Step 4) Parse only lines that start with "Series"
//
// The lines we want look like:
//    Series 14 Name  D4 Glyc Lac C41 5
//
// Capture series number and series name as a hash/dictionary

// Create a map for "series #: series name"
def seriesMap = [:]  // empty map

def pattern = /Series\s+(\d+)\s+Name\s+(.+)/

// Read the text line by line
def lines = metadataText.readLines()

for (line in lines) {
    def matcher = (line =~ pattern)
    // If there's a match, we can capture the groups
    if (matcher) {
        // 'matcher[0][1]' is the first capturing group (series number)
        def seriesNumber = matcher[0][1].toInteger()
        // 'matcher[0][2]' is the second capturing group (the name)
        def seriesName = matcher[0][2]
        // We can also store the entire matching line:
        def entireLine = line

        // Add these to a data structure of your choice, e.g., a Map or separate lists
        seriesMap[seriesNumber] = seriesName
    }
}

// -------------------------------------------------------------------------
// Step 5) Build a checkbox dialog with these series names
if (seriesMap.isEmpty()) {
    IJ.log("No lines matched the pattern 'Series # Name ...'")
    return
}

// Sort keys in ascending order
def sortedKeys = seriesMap.keySet().sort()


GenericDialog gd = new GenericDialog("Select Series to Process")

// Create a container panel with BorderLayout
Panel container = new Panel(new BorderLayout())

// Make a checkbox for each integer key, but display the dictionary value:
//sortedKeys.each { k ->
//    gd.addCheckbox(seriesMap[k], false)
//}

// Create a panel for checkboxes with a GridLayout (one column)
Panel checkBoxPanel = new Panel(new GridLayout(0, 1))

// Store references to each Checkbox in a list
def checkboxes = []

sortedKeys.each { k ->
    def cb = new Checkbox(seriesMap[k], false)
    checkboxes << cb
    checkBoxPanel.add(cb)
}

// Create a ScrollPane and add the checkBoxPanel to it
ScrollPane scrollPane = new ScrollPane()
scrollPane.add(checkBoxPanel)
// Set the size of the scroll pane so that scrolling is activated when needed
scrollPane.setSize(300, 600)

// Add the scroll pane to the container panel
container.add(scrollPane, BorderLayout.CENTER)

// Add the container panel to the GenericDialog
gd.addPanel(container)



gd.showDialog()

if (gd.wasCanceled()) {
    IJ.log("User canceled.")
    return
}

// Now retrieve user choices:
//def selectedKeys = []
//sortedKeys.each { k ->
//    if (gd.getNextBoolean()) {
//        selectedKeys << k
//    }
//}

def selectedKeys = []
sortedKeys.eachWithIndex { k, idx ->
    if (checkboxes[idx].state) {
        selectedKeys << k
    }
}

// If you want the image names (dictionary values) instead of the keys:
def selectedNames = selectedKeys.collect { key -> seriesMap[key] }

IJ.log("User selected keys: ${selectedKeys}")
IJ.log("Corresponding names: ${selectedNames}")


// Create a File object for the results folder
//def resultsFolder = new File(resultsFolderPath)
// Make sure the folder exists (create if it does not)
//if (!resultsFolder.exists()) {
//    resultsFolder.mkdirs()
//}

// Iterate over names, create subfolders

selectedNames.each { name ->
    // Replace spaces with underscores
    def sanitizedName = name.replaceAll("\\s+", "_")
    // Create the subfolder
    def subFolder = new File(outputDir, sanitizedName)
    if (!subFolder.exists()) {
        subFolder.mkdirs()
        println("Created folder: " + subFolder.getAbsolutePath())
    } else {
        println("Folder already exists: " + subFolder.getAbsolutePath())
    }
}

//
// Why Bio-Formats Importer, why!
// recovering image metadata gives stack series numbered from 0
// loading images numbers the series from 1
//
//.  annoying...
//

def selectedSeriesString = selectedKeys.collect { k -> "series_${k+1}" }.join(" ") // need to imcrement keys by 1 when loading images
IJ.log("Selected Keys: " + selectedKeys)
IJ.log("Selected series: " + selectedSeriesString)


// Now open the selected files via `series #`

//IJ.run("Bio-Formats Importer", "open=[" + filePath + "] autoscale color_mode=Default rois_import=[ROI manager] split_channels view=Hyperstack stack_order=XYCZT series_1 series_2 series_3");

def command = "open=[" + pathToFile + "] " +
              "autoscale " +
              "color_mode=Default " +
              "rois_import=[ROI manager] " +
              "split_channels " +
              "view=Hyperstack " +
              "stack_order=XYCZT " +
              selectedSeriesString;

println("Command: " + command);
IJ.run("Bio-Formats Importer", command);

def psf_colors = ["DIC","red","yellow","blue"] as String[];
def imageList = WM.getImageTitles()

println(imageList)

//**********************************************************
//
// Iterate over image series
//
// channels = 1. Red (mRFP), 2. Yellow (YFP), 3. Blue (mTQ2)
// Need to build in check of series against metadata
// in case order is switched in other data samples
// Matters because we will use a gausian blur for the
// red channel when finding points, but not the others

selectedNames.each { name ->

    // sub-folder name from name

    def subfolderName = name.replaceAll("\\s+", "_")

    //
    // Iterate over channels
    //

    for (i in 1..3) {

        println("Name string "+ name + " C=" +i)
        //def match = imageList.find { nameString }
        def match = imageList.find { it.contains(name.trim()) && it.contains("C=" + i) }
        println("Image finding "+ match)

        // *********** Remove Scale ************

        def imp = WM.getImage(match)
        imp.removeScale();

        // *********** Deconvolution ************

        def currentPsf = new File(psfPath,"${psf_colors[i]}_psf.tif").getAbsolutePath()
        def image = " -image platform " + match
        def psf = " -psf file " + currentPsf
//        def psf = " -psf file " + psfPath + "/" + psf_colors[i] + "_psf.tif"
        def algorithm = " -algorithm RL " + deconIter
        def outputPath = new File(outputDir, subfolderName).getAbsolutePath()
//        def outputPath = "" + outputDir + "/" + subfolderName // This typew of string catenation needs to start with a string
        def outputPathParam = " -path " + outputPath
        def outputFile = "channel" + i + "deconv"
        def outputFileString = " -out stack " + outputFile

        def monitor = ""
        if (!deconMonitor) { // param to monitor deconvolution output
            monitor = " -monitor no"
        }

//        def monitor = " -monitor no" // 
        IJ.run("DeconvolutionLab2 Run", image + psf + algorithm + outputFileString + outputPathParam + monitor);

        println("Decon params" + image + psf + algorithm + outputFileString + outputPathParam)

        // *********** Z projection +  Local maximun *************

        def impZ = ZProjector.run(imp, "max")
        def newFile = new File(outputPath, "channel${i}_maxProj.tif")
        println("New file Z : "+ newFile.getAbsolutePath())
        IJ.saveAs(impZ, "Tiff", newFile.getAbsolutePath())
        impZ.show()
        if(i == redChannel) {
            IJ.run(impZ, "Gaussian Blur...", "sigma=1");
            // gausian helps remove noisy points for maxima
            // selection. Red is the only channel that seems
            // to need this
        }
        def stats = imp.getProcessor().getStatistics();
        println("The min is: " + stats.min);
        println("The mean is: " + stats.mean);
        println("The max is: " + stats.max);
        println("The sd is: " + stats.stdDev);
        
        def prominence = 3 * stats.stdDev
        IJ.run(impZ, "Find Maxima...", "prominence=" + prominence + " strict exclude output=[Point Selection]");
        IJ.run("Set Measurements...", "centroid redirect=None decimal=3");
        IJ.run(impZ, "Measure", "");
        def newFile2 = new File(outputPath, "pointsC${i}.csv")
        println("New file points : "+ newFile2.getAbsolutePath())
        
        IJ.saveAs("Results", newFile2.getAbsolutePath());
        IJ.run("Clear Results");

        // *********** Blue channel mask ********

        // trigger when blue channel is reached

        if(i == blueChannel) {

            // current imp is unaltered Z stack of CFP

            //def binSave = outputDir.getAbsolutePath() + "/channel${blueChannel}_binaryMask.tif"
            def binFile = new File(outputPath, "channel${i}_binaryMask.tif")

            IJ.run(imp, "Gaussian Blur...", "sigma=2 stack");
            ImageConverter.setDoScaling(true);
            IJ.run(imp, "8-bit", "");
            IJ.setAutoThreshold(imp, "" + cfpBinThrMethod + " dark no-reset stack");
            IJ.run(imp, "Convert to Mask", "method=" + cfpBinThrMethod + " background=Dark black");
            imp.show();
            println("Threshhold blue channel with "+ cfpBinThrMethod + " protocol");
            
            //IJ.run("Close");
            IJ.saveAs(imp, "Tiff", binFile.getAbsolutePath());
            
            // cleanup
            
            //IJ.run("Close All");
        }

    }

}
