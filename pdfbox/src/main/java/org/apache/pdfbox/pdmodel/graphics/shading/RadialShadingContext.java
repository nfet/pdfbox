/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.pdfbox.pdmodel.graphics.shading;

import java.awt.PaintContext;
import java.awt.Point;
import java.awt.color.ColorSpace;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBoolean;
import org.apache.pdfbox.pdmodel.common.function.PDFunction;
import org.apache.pdfbox.pdmodel.graphics.color.PDColorSpace;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceN;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.graphics.color.PDSeparation;
import org.apache.pdfbox.util.Matrix;

/**
 * This class represents the PaintContext of an radial shading.
 * 
 * @author lehmi
 * @version $Revision: $
 * 
 */
public class RadialShadingContext implements PaintContext 
{

    private ColorModel colorModel;
    private PDFunction function;
    private ColorSpace shadingColorSpace;
    private PDFunction shadingTinttransform;

    private AffineTransform transformAT = null;
    private Matrix currentCTM = null;
    private int currentPageHeight;
    private float maximumHeight;
    private float clippingHeight;

    private float[] domain;
    private boolean[] extend;
    private float x0;
    private float x1;
    private float y0;
    private float y1;
    private float r0;
    private float r1;

    private float d1d0;
    private double denom;
    
    /**
     * Log instance.
     */
    private static final Log LOG = LogFactory.getLog(RadialShadingContext.class);

    /**
     * Constructor creates an instance to be used for fill operations.
     * 
     * @param shadingType3 the shading type to be used
     * @param colorModelValue the color model to be used
     * @param xform transformation for user to device space
     * @param ctm current transformation matrix
     * @param pageHeight height of the current page
     * 
     */
    public RadialShadingContext(PDShadingType3 shadingType3, ColorModel colorModelValue, 
            AffineTransform xform, Matrix ctm, int pageHeight, Matrix shMatrix, float clipHeight) 
    {
        float[] coords = shadingType3.getCoords().toFloatArray();
        x0 = coords[0];
        y0 = coords[1];
        r0 = coords[2];
        x1 = coords[3];
        y1 = coords[4];
        r1 = coords[5];
        if (clipHeight > 0)
        {
            maximumHeight = 0;
            clippingHeight = clipHeight;
        }
        else
        {
            maximumHeight = Math.max((y1+r1),(y0+r0)) - Math.min((y1-r1), (y0-r0));
            clippingHeight = 0;
        }
        // transformation
//        if (shMatrix != null)
//        {
//            transformAT = xform;
//            transformAT.translate(0, -maximumHeight);
//            transformAT.concatenate(shMatrix.createAffineTransform());
//        }
//        else if (currentCTM != null)
//        {
//            transformAT = ctm.createAffineTransform();
//            transformAT.translate(0, -clippingHeight);
//            transformAT.concatenate(xform);
//        }
//        else
//        {
//            transformAT = xform;
//            transformAT.translate(0, -clipHeight);
//        }
        try
        {
            transformAT = xform.createInverse();
        } 
        catch (NoninvertibleTransformException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        currentPageHeight = pageHeight;
        // colorSpace 
        try 
        {
            PDColorSpace cs = shadingType3.getColorSpace();
            if (!(cs instanceof PDDeviceRGB))
            {
                // we have to create an instance of the shading colorspace if it isn't RGB
                shadingColorSpace = cs.getJavaColorSpace();
                if (cs instanceof PDDeviceN)
                {
                    shadingTinttransform = ((PDDeviceN)cs).getTintTransform();
                }
                else if (cs instanceof PDSeparation)
                {
                    shadingTinttransform = ((PDSeparation)cs).getTintTransform();
                }
            }
        } 
        catch (IOException exception) 
        {
            LOG.error("error while creating colorSpace", exception);
        }
        // colorModel
        if (colorModelValue != null)
        {
            colorModel = colorModelValue;
        }
        else
        {
            try
            {
                // TODO bpc != 8 ??  
                colorModel = shadingType3.getColorSpace().createColorModel(8);
            }
            catch(IOException exception)
            {
                LOG.error("error while creating colorModel", exception);
            }
        }
        // shading function
        try
        {
            function = shadingType3.getFunction();
        }
        catch(IOException exception)
        {
            LOG.error("error while creating a function", exception);
        }
        // domain values
        if (shadingType3.getDomain() != null)
        {
            domain = shadingType3.getDomain().toFloatArray();
        }
        else 
        {
            // set default values
            domain = new float[]{0,1};
        }
        // extend values
        COSArray extendValues = shadingType3.getExtend();
        if (shadingType3.getExtend() != null)
        {
            extend = new boolean[2];
            extend[0] = ((COSBoolean)extendValues.get(0)).getValue();
            extend[1] = ((COSBoolean)extendValues.get(1)).getValue();
        }
        else
        {
            // set default values
            extend = new boolean[]{false,false};
        }
        // calculate some constants to be used in getRaster
        denom = Math.pow(r1-r0,2) - Math.pow(x1-x0,2) - Math.pow(y1-y0,2);
        d1d0 = domain[1]-domain[0];
        // TODO take a possible Background value into account
        
    }
    /**
     * {@inheritDoc}
     */
    public void dispose() 
    {
        colorModel = null;
        function = null;
        shadingColorSpace = null;
        shadingTinttransform = null;
        currentCTM = null;
    }

    /**
     * {@inheritDoc}
     */
    public ColorModel getColorModel() 
    {
        return colorModel;
    }

    /**
     * {@inheritDoc}
     */
    public Raster getRaster(int x, int y, int w, int h) 
    {
        float[] input = new float[1];
        float inputValue = 0;
        int[] data = new int[w * h * 3];
        for (int j = 0; j < h; j++) 
        {
            for (int i = 0; i < w; i++) 
            {
                float[] inputValues = calculateInputValues( x+i, y+j);
                // choose 1 of the 2 values
                if (inputValues[0] >= domain[0] && inputValues[0] <= domain[1])
                {
                    // both values are in the domain -> choose the larger one 
                    if(inputValues[1] >= domain[0] && inputValues[1] <= domain[1])
                    {
                        inputValue = Math.max(inputValues[0], inputValues[1]);
                    } 
                    // first value is in the domain, the second not -> choose first value
                    else
                    {
                        inputValue = inputValues[0];
                    }
                }
                else
                {
                    // first value is not in the domain, but the second -> choose second value
                    if(inputValues[1] >= domain[0] && inputValues[1] <= domain[1])
                    {
                        inputValue = inputValues[1];
                    }
                    // both are not in the domain
                    else
                    {
                        if (!extend[0] && !extend[1])
                        {
                            // TODO background
                            continue;
                        }
                        boolean extended = false;
                        // extend
                        if (extend[0])
                        {
                            if((r0 + inputValues[0]*(r1-r0)) >= 0 && inputValues[0] < 0)
                            {
                                inputValue = domain[0];
                                extended = true;
                            }
                            else if ((r0 + inputValues[1]*(r1-r0)) >= 0 && inputValues[1] < 0)
                            {
                                inputValue = domain[0];
                                extended = true;
                            }
                        }
                        if (!extended && extend[1])
                        {
                            if((r0 + inputValues[0]*(r1-r0)) >= 0 && inputValues[0] > 0)
                            {
                                inputValue = domain[1];
                                extended = true;
                            }
                            else if((r0 + inputValues[1]*(r1-r0)) >= 0 && inputValues[1] > 0)
                            {
                                inputValue = domain[1];
                                extended = true;
                            }
                        }
                        if (!extended)
                        {
                            continue;
                        }
                    }
                }
                input[0] = (float)(domain[0] + (d1d0*inputValue));
                float[] values = null;
                int index = (j * w + i) * 3;
                try 
                {
                    values = function.eval(input);
                    // convert color values from shading colorspace to RGB 
                    if (shadingColorSpace != null)
                    {
                        if (shadingTinttransform != null)
                        {
                            values = shadingTinttransform.eval(values);
                        }
                        values = shadingColorSpace.toRGB(values);
                    }
                }
                catch (IOException exception) 
                {
                    LOG.error("error while processing a function", exception);
                }
                data[index] = (int)(values[0]*255);
                data[index+1] = (int)(values[1]*255);
                data[index+2] = (int)(values[2]*255);
            }
        }
        // create writable raster
        WritableRaster raster = getColorModel().createCompatibleWritableRaster(w, h);
        raster.setPixels(0, 0, w, h, data);
        return raster;
    }

    private float[] calculateInputValues(int x, int y) 
    {
        
        /** 
         *  According to Adobes Technical Note #5600 we have to do the following 
         *  
         *  x0, y0, r0 defines the start circle
         *  x1, y1, r1 defines the end circle
         *  
         *  The parametric equations for the center and radius of the gradient fill
         *  circle moving between the start circle and the end circle as a function 
         *  of s are as follows:
         *  
         *  xc(s) = x0 + s * (x1 - x0)
         *  yc(s) = y0 + s * (y1 - y0)
         *  r(s)  = r0 + s * (r1 - r0)
         * 
         *  Given a geometric coordinate position (x, y) in or along the gradient fill, 
         *  the corresponding value of s can be determined by solving the quadratic 
         *  constraint equation:
         *  
         *  [x - xc(s)]2 + [y - yc(s)]2 = [r(s)]2
         *  
         *  The following code calculates the 2 possible values of s
         */
        
//        float[] srcPoint = new float[] {x, currentPageHeight + maximumHeight + clippingHeight - y };
      float[] srcPoint = new float[] {x, currentPageHeight - y };
//        float[] srcPoint = new float[] {x,y};
        float[] dstPoint = new float[2];
        transformAT.transform(srcPoint, 0, dstPoint, 0, 1);
        // -p/2
        float p = r0*(r1-r0) + (dstPoint[0]-x0)*(x1-x0) + (dstPoint[1]-y0)*(y1-y0);
        p /= -denom;
        // q
        float q = (float)(Math.pow(r0,2) - Math.pow((dstPoint[0]-x0),2) -Math.pow((dstPoint[1]-y0),2)); 
        q /= denom;
        // root
        float root = (float)Math.sqrt(Math.pow(p , 2) - q);
        // results
        if (denom > 0)
            return new float[]{(p - root), (p + root)};
        else
            return new float[]{(p + root), (p - root)};
    }
}
