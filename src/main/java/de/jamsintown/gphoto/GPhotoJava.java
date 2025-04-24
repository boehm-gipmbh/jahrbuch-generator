package de.jamsintown.gphoto;

import org.gphoto2.*;

import java.io.File;

import static org.gphoto2.Camera.getLibraryVersion;

public class GPhotoJava {

    public static void main(String[] args) {
        System.out.println("GPhoto version: " + getLibraryVersion());
        final CameraList cl = new CameraList();
        System.out.println("Cameras: " + cl);
        CameraUtils.closeQuietly(cl);
        final Camera c = new Camera();
        c.initialize();
        CameraWidgets cameraWidgets = c.newConfiguration();
        //  printWidgetconfig(cameraWidgets);
        //  printInspect(cameraWidgets);
        printChoices(cameraWidgets);
        final CameraFile cf2 = c.captureImage();
        cf2.save(new File("captured.jpg").getAbsolutePath());
        CameraUtils.closeQuietly(cf2);
        CameraUtils.closeQuietly(c);
    }

    private static void printChoices(CameraWidgets cameraWidgets) {
        cameraWidgets.getNames()
                .forEach(name -> {
                    switch (cameraWidgets.getType(name)) {
                        case CameraWidgets.WidgetTypeEnum.Menu:
                            System.out.println(cameraWidgets.getType(name).name().toUpperCase() + ": " + name + " = " + cameraWidgets.getValue(name));
                            System.out.println("Choices: " + cameraWidgets.listChoices(name));
                            System.out.println("Info: " + cameraWidgets.getInfo(name));
                            System.out.println("Label: " + cameraWidgets.getLabel(name));
                            break;
                        case CameraWidgets.WidgetTypeEnum.Radio:
                            System.out.println(cameraWidgets.getType(name).name().toUpperCase() + ": " + name + " = " + cameraWidgets.getValue(name));
                            System.out.println("Choices: " + cameraWidgets.listChoices(name));
                            System.out.println("Info: " + cameraWidgets.getInfo(name));
                            System.out.println("Label: " + cameraWidgets.getLabel(name));
                            break;
                        case CameraWidgets.WidgetTypeEnum.Section:
                            System.out.println(cameraWidgets.getType(name).name().toUpperCase() + ": " + name + " = " + cameraWidgets.getValue(name));
                            System.out.println("Info: " + cameraWidgets.getInfo(name));
                            System.out.println("Label: " + cameraWidgets.getLabel(name));
                            break;

                        case CameraWidgets.WidgetTypeEnum.Date:
                            System.out.println(cameraWidgets.getType(name).name().toUpperCase() + ": " + name + " = " + cameraWidgets.getValue(name));
                            System.out.println("Info: " + cameraWidgets.getInfo(name));
                            System.out.println("Label: " + cameraWidgets.getLabel(name));
                            break;

                        case CameraWidgets.WidgetTypeEnum.Text:
                            System.out.println(cameraWidgets.getType(name).name().toUpperCase() + ": " + name + " = " + cameraWidgets.getValue(name));
                            System.out.println("Info: " + cameraWidgets.getInfo(name));
                            System.out.println("Label: " + cameraWidgets.getLabel(name));
                            break;
                        case CameraWidgets.WidgetTypeEnum.Button:
                            System.out.println(cameraWidgets.getType(name).name().toUpperCase() + ": " + name + " = " + cameraWidgets.getValue(name));
                            System.out.println("Info: " + cameraWidgets.getInfo(name));
                            System.out.println("Label: " + cameraWidgets.getLabel(name));
                            break;
                        case CameraWidgets.WidgetTypeEnum.Toggle:
                            System.out.println(cameraWidgets.getType(name).name().toUpperCase() + ": " + name + " = " + cameraWidgets.getValue(name));
                            System.out.println("Info: " + cameraWidgets.getInfo(name));
                            System.out.println("Label: " + cameraWidgets.getLabel(name));
                            break;
                        case CameraWidgets.WidgetTypeEnum.Window:
                            System.out.println(cameraWidgets.getType(name).name().toUpperCase() + ": " + name + " = " + cameraWidgets.getValue(name));
                            System.out.println("Info: " + cameraWidgets.getInfo(name));
                            System.out.println("Label: " + cameraWidgets.getLabel(name));
                            break;

                        case CameraWidgets.WidgetTypeEnum.Range:
                            System.out.println(cameraWidgets.getType(name).name().toUpperCase() + ": " + name + " = " + cameraWidgets.getValue(name));
                            System.out.println("Range: " + cameraWidgets.getRange(name));
                            System.out.println("Info: " + cameraWidgets.getInfo(name));
                            System.out.println("Label: " + cameraWidgets.getLabel(name));

                            break;

                        default:
                            System.out.println("OTHER: " + cameraWidgets.getType(name).name().toUpperCase() + ": " + name + " = " + cameraWidgets.getValue(name));
                            System.out.println("Info: " + cameraWidgets.getInfo(name));
                            break;
                    }

                });
    }

    private static void printInspect(CameraWidgets cameraWidgets) {
        String inspect = cameraWidgets.inspect();
        System.out.println(inspect);
    }

    private static void printWidgetconfig(CameraWidgets cameraWidgets) {
        cameraWidgets.getNames()
                .forEach(name -> System.out.println(name + " = " + cameraWidgets.getValue(name)));
    }
}
