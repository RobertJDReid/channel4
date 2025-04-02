//********************************************************************
//
//                            ColorMage
//
//  Ok, with a name like that you would think it would do more than
//  just merge color channels, but somethimes these names get
//  out of hand...
//********************************************************************

#@File(label="Choose a file for the red channel", style="open") redFile
#@File(label="Choose a file for the yellow channel", style="open") yellowFile
#@File(label="Choose a file for the blue channel", style="open") blueFile

import ij.IJ
import ij.ImagePlus

// Open the selected file as an image
def impR = IJ.openImage(redFile.getAbsolutePath())
def impY = IJ.openImage(yellowFile.getAbsolutePath())
def impB = IJ.openImage(blueFile.getAbsolutePath())

impR.show();
impY.show();
impB.show();

String mergeArgs = "c1=" + impR.getTitle() + " c2=" + impY.getTitle() + " c3=" + impB.getTitle() + " create"
IJ.run("Merge Channels...", mergeArgs)

