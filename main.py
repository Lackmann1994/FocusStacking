import os
import cv2
import sys
import numpy as np


def homograpy(image_1_kp, image_2_kp, matches):
    image_1_points = np.zeros((len(matches), 1, 2), dtype=np.float32)
    image_2_points = np.zeros((len(matches), 1, 2), dtype=np.float32)
    for i in range(0, len(matches)):
        image_1_points[i] = image_1_kp[matches[i].queryIdx].pt
        image_2_points[i] = image_2_kp[matches[i].trainIdx].pt
    homography, mask = cv2.findHomography(image_1_points, image_2_points, cv2.RANSAC, ransacReprojThreshold=2.0)
    return homography


def alignImages(images, names):
    outimages = []
    detector = cv2.xfeatures2d.SIFT_create()
    outimages.append(images[0])
    image1gray = cv2.cvtColor(images[0], cv2.COLOR_BGR2GRAY)
    image_1_kp, image_1_desc = detector.detectAndCompute(image1gray, None)
    print "Writing in file " + names[0]
    cv2.imwrite("output/" + names[0], images[0])
    for i in range(1, len(images)):
        image_i_kp, image_i_desc = detector.detectAndCompute(images[i], None)
        bf = cv2.BFMatcher()
        pairMatches = bf.knnMatch(image_i_desc, image_1_desc, k=2)
        rawMatches = []
        for m, n in pairMatches:
            if m.distance < 0.7 * n.distance:
                rawMatches.append(m)
        sortMatches = sorted(rawMatches, key=lambda x: x.distance)
        matches = sortMatches[0:128]
        hom = homograpy(image_i_kp, image_1_kp, matches)
        newimage = cv2.warpPerspective(images[i], hom, (images[i].shape[1], images[i].shape[0]), flags=cv2.INTER_LINEAR)
        outimages.append(newimage)
        print "Writing in file " + names[i]
        cv2.imwrite("output/" + names[i], newimage)


if __name__ == "__main__":
    if len(sys.argv) < 2:
        raise Exception("Usage: main.py inputDir [samplingFactor] Example main.py Images/aligned/scene1")
    path = sys.argv[1]
    imageFiles = sorted(os.listdir(path))
    sampling = 1
    if len(sys.argv) == 3:
        if sys.argv[2] == '2':
            sampling = cv2.IMREAD_REDUCED_COLOR_2
            print "Down sacling factor 2"
        elif sys.argv[2] == '4':
            sampling = cv2.IMREAD_REDUCED_COLOR_4
            print "Down sacling factor 4"
        else:
            print "No down sacling"
    for img in imageFiles:
        if img.split(".")[-1].lower() not in ["jpg", "jpeg", "png"]:
            imageFiles.remove(img)
    focusImages = []
    for img in imageFiles:
        print "Reading in file {}".format(img)
        if sampling == 1:
            focusImages.append(cv2.imread(path + "/" + img))
        if sampling == cv2.IMREAD_REDUCED_COLOR_2:
            focusImages.append(cv2.imread(path + "/" + img, cv2.IMREAD_REDUCED_COLOR_2))
        if sampling == cv2.IMREAD_REDUCED_COLOR_4:
            focusImages.append(cv2.imread(path + "/" + img, cv2.IMREAD_REDUCED_COLOR_4))
    alignImages(focusImages, imageFiles)
