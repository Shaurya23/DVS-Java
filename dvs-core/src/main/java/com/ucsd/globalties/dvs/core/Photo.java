package com.ucsd.globalties.dvs.core;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.highgui.Highgui;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.objdetect.Objdetect;

import com.ucsd.globalties.dvs.core.tools.Pair;

@Slf4j
public class Photo {
  public enum PhotoType {
    HORIZONTAL, VERTICAL;
  }

  private PhotoType type;
  private String path;
  @Getter
  private double pupillaryDistance;

  private Pair<Eye, Eye> eyes;

  public Photo(String path, PhotoType type) {
    this.path = path;
    if (!new File(path).exists()) {
    	throw new RuntimeException("Invalid file specified: " + path);
    }
    this.type = type;
  }

  public Eye getLeftEye() {
    if (eyes == null) {
      eyes = findEyes();
    }
    return eyes.getLeft();
  }

  public Eye getRightEye() {
    if (eyes == null) {
      eyes = findEyes();
    }
    return eyes.getRight();
  }
  
  private Rect findFaceRoi(Mat image) {
    CascadeClassifier faceDetector = new CascadeClassifier(Main.HAAR_FACE_PATH);
    MatOfRect faceDetections = new MatOfRect();
    faceDetector.detectMultiScale(image, faceDetections, 1.05, 2, Objdetect.CASCADE_FIND_BIGGEST_OBJECT | Objdetect.CASCADE_SCALE_IMAGE, new Size(30,30), new Size(image.width(),image.height()));

    log.info(String.format("Detected %s faces for img: %s", faceDetections.toArray().length, path));
    Rect detectedFace;
    if (faceDetections == null || faceDetections.toArray().length == 0) {
      // go straight into eye detection on current image if no face is found
      return null;
    }
    detectedFace = faceDetections.toArray()[0];
    Rect faceBox = new Rect(detectedFace.x, detectedFace.y, detectedFace.width, (detectedFace.height * 2) / 3);
    //Highgui.imwrite("face_"+ type + ".jpg", new Mat(image, faceBox));
    return faceBox;
  }

  public Pair<Eye, Eye> findEyes() {
    Mat image = Highgui.imread(path);
    // find face
    Rect faceBox = findFaceRoi(image);
    // Detect eyes from cropped face image
    CascadeClassifier eyeDetector = new CascadeClassifier(Main.HAAR_EYE_PATH);
    MatOfRect eyeDetections = new MatOfRect();
    Mat faceImage = faceBox != null ? new Mat(image, faceBox) : image;
    eyeDetector.detectMultiScale(faceImage, eyeDetections);

    List<Rect> detectedEyes = eyeDetections.toList();
    log.info(String.format("Detected %s eyes for img: %s", detectedEyes.size(), path));
    List<Rect> eyes = new ArrayList<>(2);
    if (detectedEyes.size() > 2) { // found an extra eye or two
      detectedEyes.sort(new RectAreaCompare());
      // we can safely get the last 2 biggest ones, because after the crop the eyes take up the most space
      eyes.add(detectedEyes.get(detectedEyes.size() - 1));
      eyes.add(detectedEyes.get(detectedEyes.size() - 2));
      // TODO maybe add some more criteria here and have criterion weights for more accurate behavior
    } else {
      eyes.addAll(eyeDetections.toList());
    }
    eyes.sort(new EyeXCompare()); // simple sort to know which eye is left and which is right
    Mat leftEyeMat = new Mat(faceImage, eyes.get(0));
    Mat rightEyeMat = new Mat(faceImage, eyes.get(1));
    //Highgui.imwrite("left_eye_" + type + ".jpg", leftEyeMat);
    //Highgui.imwrite("right_eye_" + type + ".jpg", rightEyeMat);    
    log.info("created left eye mat: " + leftEyeMat);    
    log.info("created right eye mat: " + rightEyeMat);
    java.awt.Point leftCenter = new java.awt.Point(leftEyeMat.width() / 2, leftEyeMat.height() / 2);
    java.awt.Point rightCenter = new java.awt.Point(rightEyeMat.width() / 2, rightEyeMat.height() / 2);
    // initialize a rough estimate of pupillary distance here, when we detect the eyes.
    // We can adjust it to an accurate value when pupils are detected.
    pupillaryDistance = leftCenter.distance(rightCenter);
    return new Pair<Eye, Eye>(new Eye(leftEyeMat), new Eye(rightEyeMat));
  }
  
  public void adjustPupillaryDistance(double val) {
    pupillaryDistance += val;
  }

  private static class RectAreaCompare implements Comparator<Rect> {
    public int compare(Rect r1, Rect r2) {
      int r1Area = r1.height * r1.width;
      int r2Area = r2.height * r2.width;
      return r1Area < r2Area ? -1 : r1Area == r2Area ? 0 : 1;
    }
  }

  private static class EyeXCompare implements Comparator<Rect> {
    public int compare(Rect r1, Rect r2) {
      return r1.x < r2.x ? -1 : r1.x == r2.x ? 0 : 1;
    }
  }
  
}
