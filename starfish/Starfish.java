/*
This program is free software: you can redistribute it and/or modify it under 
the terms of the GNU General Public License as published by the Free Software 
Foundation, either version 3 of the License, or (at your option) any later 
version.

This program is distributed in the hope that it will be useful, but WITHOUT 
ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS 
FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
You should have received a copy of the GNU General Public License along with 
this program. If not, see <https://www.gnu.org/licenses/>.
*/

package starfish;

import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import javax.swing.filechooser.FileFilter;
import javax.swing.plaf.nimbus.NimbusLookAndFeel;

class RandomSingleton 
{
    private static RandomSingleton instance;
    private Random rnd;

    private RandomSingleton() 
    {
        rnd = new Random();
    }

    public static RandomSingleton getInstance() 
    {
        if(instance == null) 
        {
            instance = new RandomSingleton();
        }
        return instance;
    }

    public int nextInt(int n) 
    {
        return rnd.nextInt(n);
    }

    public double nextDouble() 
    {
        return rnd.nextDouble();
    }
}

class PathSingleton 
{
    private static PathSingleton instance;
    private static File path;

    private PathSingleton() 
    {
        path = new File("");
    }

    public static PathSingleton getInstance() 
    {
        if(instance == null) 
        {
            instance = new PathSingleton();
        }
        return instance;
    }

    public static void setPath(File p)
    {
        path = p;
    }

    public static File getPath()
    {
        return path;
    }
}

class pixel
{
    int red;
    int green;
    int blue;

    pixel(int red, int green, int blue)
    {
        this.red = red;
        this.green = green;
        this.blue = blue;
    }	
}

class StarfishPalette
{
    int colourCount;
    pixel[] colour = new pixel[256];
}

enum AAMode
{
    AAMODE_NONE,
    AAMODE_2X,
    AAMODE_4X
}

interface ImageLayer 
{
    pixel value(double x, double y);
}

interface LinearWave
{
    double value(double d);
}

interface PlanarWave 
{
    double value(double x, double y);
}

class Coswave implements LinearWave
{
    RandomSingleton r = RandomSingleton.getInstance();
    double mPhase = r.nextDouble() * Math.PI;
    double mPeriod = Math.PI / Math.pow(r.nextDouble(), 0.5);

    public double value (double d)
    {
        return Math.cos(d * mPeriod + mPhase);
    }
}

class Sawtooth implements LinearWave
{
    RandomSingleton r = RandomSingleton.getInstance();
    double mPeriod = 1.0 / Math.pow(r.nextDouble(), 0.5);
    double mPhase  = r.nextDouble() * 2.0;
    double mFlipSign = (r.nextDouble() >= 0.5) ? 1.0 : -1.0;

    public double value(double d) 
    {
        d = (d + mPhase) * mPeriod;
        d = d - Math.floor(d);
        d = (d * 2.0) - 1.0;
        return d * mFlipSign; 
    }
}

class Ess implements LinearWave
{
    RandomSingleton r = RandomSingleton.getInstance();
    double mAcceleration = (r.nextDouble() >= 0.5) ? r.nextDouble() :
    	1.0 / (1.0 - r.nextDouble());
    double mFlipSign = (r.nextDouble() >= 0.5) ? 1.0 : -1.0;

    public double value(double d)
    {
        return ((2.0/(mAcceleration*d*d+1.0))-1.0) * mFlipSign;
    }
}	

class InvertWave implements LinearWave
{
    LinearWave mSource;
    InvertWave(LinearWave target)
    {
        mSource = target;
    }

    public double value(double d)
    {
        return -mSource.value(d);
    }
}	

class InsertWavePeaks implements LinearWave
{
    LinearWave mSource;
    RandomSingleton r = RandomSingleton.getInstance();
    double mScale = (r.nextDouble() * r.nextDouble() * 8.0) + 1.0;
    boolean mProcessSign = r.nextDouble() >= 0.5;
    InsertWavePeaks(LinearWave target)
    {
        mSource = target;
    }	

    public double value(double d)
    {
        double skt = mSource.value(d);
        if (mProcessSign)
        {
            skt = (skt + 1.0) / 2.0;
        }
        skt = skt * mScale;
        if (skt < 0)
        {
            skt = skt - Math.ceil(skt);
        }
        else
        {
            skt = skt - Math.floor(skt);
        }
        if (mProcessSign)
        {
            skt = (skt * 2.0) - 1.0;
        }
        return skt;
    }
}	

class Modulator implements LinearWave
{
    LinearWave mSource;
    LinearWave mWobbler;
    Modulator(LinearWave target, LinearWave wobbler)
    {
        mSource = target;
        mWobbler = wobbler;
    }

    public double value(double d)
    {
        return mSource.value(d + mWobbler.value(d));
    }
}	

class MixLinear implements LinearWave
{
	RandomSingleton r = RandomSingleton.getInstance();
    double mAFactor= r.nextDouble();
    double mBFactor = r.nextDouble();
    double mSumFactor = mAFactor + mBFactor;
    LinearWave mAWave;
    LinearWave mBWave;

    MixLinear(LinearWave a, LinearWave b)
    {
        mAWave = a;
        mBWave = b;
    }

    public double value(double d)
    {
        return (mAWave.value(d) * mAFactor + mBWave.value(d) * mBFactor) / mSumFactor;
    }
}

class MinimaxLinear implements LinearWave
{
	RandomSingleton r = RandomSingleton.getInstance();
	boolean mMin = r.nextDouble() >= 0.5;
    LinearWave mASrc;
    LinearWave mBSrc;

    MinimaxLinear(LinearWave a, LinearWave b)
    {
        mASrc = a;
        mBSrc = b;
    }

    public double value(double d)
    {
        if (mMin)
        {
            return Math.min(mASrc.value(d), mBSrc.value(d));
        }
        else
        {
            return Math.max(mASrc.value(d), mBSrc.value(d));
        }
    }
}

class MultiplyLinear implements LinearWave
{
    LinearWave mASrc;
    LinearWave mBSrc;

    MultiplyLinear(LinearWave a, LinearWave b)
    {
        mASrc = a;
        mBSrc = b;
    }

    public double value(double d)
    {
        return mASrc.value(d) * mBSrc.value(d);
    }
}	

class GammaLinear implements LinearWave
{
	RandomSingleton r = RandomSingleton.getInstance();
    double mExp = 1.0 /(r.nextDouble() * 2.0);
    LinearWave mSource;

    GammaLinear(LinearWave target)
    {
        mSource = target;
    }

    public double value(double d) 
    {
        double cpf;
        cpf = (mSource.value(d) + 1.0) / 2.0;
        cpf = Math.pow(cpf, mExp);
        return cpf * 2.0 + - 1.0;
    }
}

class Pebbledrop implements PlanarWave
{
    LinearWave mSource;

    Pebbledrop(LinearWave target)
    {
        mSource = target;
    }

    public double value(double x, double y)
    {
        double hypotenuse = Math.sqrt(x*x + y*y);
        return mSource.value(hypotenuse);
    }
}	

class Curtain implements PlanarWave
{
    LinearWave mSource;

    Curtain(LinearWave source)
    {
        mSource = source;
    }

    public double value(double x, double y) 
    {
        return mSource.value(x);
    }
}

class Zigzag implements PlanarWave
{
	RandomSingleton r = RandomSingleton.getInstance();
    double mAmplitude  = r.nextDouble();
    LinearWave mOscillator;
    LinearWave mSource;

    Zigzag(LinearWave source, LinearWave oscillator)
    {
        mSource = source;
        mOscillator = oscillator;
    }

    public double value(double x, double y)
    {
        return mSource.value(x + mOscillator.value(y) * mAmplitude);
    }
}

class Starfish2 implements PlanarWave
{
	RandomSingleton r = RandomSingleton.getInstance();
    double mAmplitude = r.nextDouble();
    double mAttenuation = 1.0 / r.nextDouble();
    double mSpinRate = r.nextDouble();
    LinearWave mOscillator;
    LinearWave mSource;

    Starfish2(LinearWave source, LinearWave oscillator)
    {
        mSource = source;
        mOscillator = oscillator;
    }

    public double value(double x, double y)
    {
        double angle = Math.atan2(y, x) * mSpinRate;
        double hypotenuse = Math.sqrt(x*x + y*y);
        double amp = mAmplitude * (1.0 - (1.0 / (mAttenuation * hypotenuse * hypotenuse + 1.0)));
        return mSource.value(hypotenuse + mOscillator.value(angle) * amp);
    }
}

class Spinflake implements PlanarWave
{
	RandomSingleton r = RandomSingleton.getInstance();
    double mAmplitude = Math.pow(r.nextDouble(), 4.0) + 0.05;
    double mRadius = Math.pow(r.nextDouble(), 3.0) + 1.0;
    double mSharpness = r.nextDouble() * 10.0;
    double mSignflip = (r.nextDouble()) >= 0.5 ? 1.0 : -1.0;
    LinearWave mSource;

    Spinflake(LinearWave source)
    {
        mSource = source;
    }

    public double value(double x, double y) 
    {
        double value;
        double hypotenuse = Math.sqrt(x*x + y*y);
        double angle = Math.atan2(y,x);
        hypotenuse = hypotenuse + mSource.value(angle) * mAmplitude;
        if (hypotenuse < 0) hypotenuse = 0;
        if (hypotenuse > mRadius)
        {
            value = Math.atan(hypotenuse - mRadius) / (Math.PI/2.0);
        }
        else
        {
            value = 1.0 - Math.pow(hypotenuse / mRadius, mSharpness);
        }
        return mSignflip * ((value * 2.0) - 1.0);
    }
}

class InvertPlane implements  PlanarWave
{
    PlanarWave mSource;

    InvertPlane(PlanarWave source)
    {
        mSource = source;
    }

    public double value(double x, double y)
    {
        return -mSource.value(x, y);
    }
}	

class MinimaxPlanar implements  PlanarWave
{
	RandomSingleton r = RandomSingleton.getInstance();
	boolean mMin = r.nextDouble() >= 0.5;
    PlanarWave mASrc;
    PlanarWave mBSrc;
    
    MinimaxPlanar(PlanarWave a, PlanarWave b)
    {
        mASrc = a;
        mBSrc = b;
    }

    public double value(double x, double y)
    {
        if (mMin)
        {
            return Math.min(mASrc.value(x,y), mBSrc.value(x,y));
        }
        else
        {
            return Math.max(mASrc.value(x,y), mBSrc.value(x,y));
        }
    }
}

class MixPlanar implements  PlanarWave
{
	RandomSingleton r = RandomSingleton.getInstance();
    double mABias = r.nextDouble();
    double mBBias = 1.0 - mABias;
    PlanarWave mASrc;
    PlanarWave mBSrc;

    MixPlanar(PlanarWave a, PlanarWave b)
    {
        mASrc = a;
        mBSrc = b;
    }

    public double value(double x, double y)
    {
        return mASrc.value(x,y) * mABias + mBSrc.value(x,y) * mBBias;
    }
}

class WarpPlane implements  PlanarWave
{
	 RandomSingleton r = RandomSingleton.getInstance();
    double mAcceleration = r.nextDouble();
    double mAmplitude = r.nextDouble();
    double mAttenuation = 1.0 / Math.pow(r.nextDouble(), 2.0);
    LinearWave mModulator;
    PlanarWave mSource;

    WarpPlane(PlanarWave source, LinearWave modulator)
    {
        mSource = source;
        mModulator = modulator;
    }

    public double value(double x, double y)
    {
        double amp = mAmplitude / (mAttenuation * y * y + 1.0);
        y = y + mModulator.value(x * mAcceleration) * amp;
        return mSource.value(x, y);
    }
}	

class Reflector implements  PlanarWave
{
	RandomSingleton r = RandomSingleton.getInstance();
    int mMode = r.nextInt(3);
    PlanarWave mSource;

    Reflector(PlanarWave source)
    {
        mSource = source;
    }

    public double value(double x, double y) 
    {
        double ty = y;
        switch (mMode)
        {
            case 0:
            		if (x < 0) ty = -y;
            		break;
            		
            case 1:
            		ty = Math.abs(ty);
           		break;
           		
           	default:
           		break;
        }
        return mSource.value(Math.abs(x), ty);
    }
}

class GammaPlanar implements  PlanarWave
{
	RandomSingleton r = RandomSingleton.getInstance();
    double mExp = 1.0 / (r.nextDouble() * 2.0);
    PlanarWave mSource;

    GammaPlanar(PlanarWave source)
    {
        mSource = source;
    }

    public double value(double x, double y) 
    {
        double cpf;
        cpf = (mSource.value(x, y) + 1.0) / 2.0;
        cpf = Math.pow(cpf, mExp);
        return cpf * 2.0 + - 1.0;
    }
}

class MultiplyPlanar implements  PlanarWave
{
    PlanarWave mASrc;
    PlanarWave mBSrc;

    MultiplyPlanar(PlanarWave a, PlanarWave b)
    {
        mASrc = a;
        mBSrc = b;
    }

    public double value(double x, double y) 
    {
        return mASrc.value(x, y) * mBSrc.value(x, y);
    }
}

class Quadratesselator implements  PlanarWave
{
	RandomSingleton r = RandomSingleton.getInstance();
    double mHSize = (4.0 / r.nextDouble()) - 4.0;
    double mVSize = (4.0 / r.nextDouble()) - 4.0;
    PlanarWave mSource;

    Quadratesselator(PlanarWave source)
    {
        mSource = source;
    }

    public double value(double x, double y) 
    {
        x = (x + 1.0) / 2.0;
        y = (y + 1.0) / 2.0;
        x = x * mHSize;
        y = y * mVSize;
        x = x - Math.floor(x);
        y = y - Math.floor(y);
        x = x / mHSize;
        y = y / mVSize;
        x = (x * 2.0) - 1.0;
        y = (y * 2.0) - 1.0;
        return mSource.value(x, y);
    }
}

class Hexatesselator implements  PlanarWave
{
	RandomSingleton r = RandomSingleton.getInstance();
    double mScale = 1.0 / Math.pow((r.nextDouble()*0.9)+0.1, 3.0);
    double cosThirdPi= 0.5;
    double sinThirdPi = 0.866025;
    double twiceSinThirdPi = 1.73205;
    double tanThirdPi = 1.73205;
    PlanarWave mSource;

    Hexatesselator(PlanarWave source)
    {
        mSource = source;
    }

    public double value(double x, double y) 
    {
        x = x * mScale;
        y = y * mScale;
        x = (x + sinThirdPi) / twiceSinThirdPi;
        x = x - Math.floor(x);
        x = (x * twiceSinThirdPi) - sinThirdPi;
        y = (y + 2.0) / 3.0;
        y = y - Math.floor(y);
        y = (y * 3.0) - 2.0;

        double dx, dy;
        if (y - cosThirdPi > Math.abs(x) / tanThirdPi)
        {
            dx = 0;
            dy = -2 + cosThirdPi;
        }
        else if (-y -cosThirdPi> Math.abs(x) / tanThirdPi)
        {
            dx = 0;
            dy = 1.0 + cosThirdPi;
        }
        else if (x < 0)
        {
            dx = sinThirdPi;
            dy = 0;
        }
        else
        {
            dx = -sinThirdPi;
            dy = 0;
        }
        return mSource.value(x + dx, y + dy);
    }
}

class Rotawarp implements  PlanarWave
{
	RandomSingleton r = RandomSingleton.getInstance();
    double mAmplitude = r.nextDouble() * 2.0;
    PlanarWave mSource;
    LinearWave mWarp;

    Rotawarp(PlanarWave source, LinearWave warp)
    {
        mSource = source;
        mWarp = warp;
        if (mAmplitude > 1.0)
        {
            mAmplitude = 1.0 / Math.pow(mAmplitude - 1.0, 1.0);
        }
    }

    public double value(double x, double y) 
    {
        double angle = Math.atan2(y, x);
        double hyp = Math.sqrt(x*x + y*y);
        angle = angle + mWarp.value(hyp) * mAmplitude;
        return mSource.value(hyp * Math.cos(angle), hyp * Math.sin(angle));
    }
}

class Mixmaster implements  PlanarWave
{
    PlanarWave mSource;
    RandomSingleton r = RandomSingleton.getInstance();
    double mXOff = r.nextDouble() * 2.0 - 1.0;
    double mYOff  = r.nextDouble() * 2.0 - 1.0;
    double mCosXFact, mSinXFact, mCosYFact, mSinYFact;   

    Mixmaster(PlanarWave source)
    {
        mSource = source;
        double angle = r.nextDouble() * (Math.PI*2.0);
        double sinangle = Math.sin(angle);
        double cosangle = Math.cos(angle);

        double xfactor, yfactor;
        if (r.nextDouble() >= 0.5)
        {
            xfactor = r.nextDouble() + 0.1;
            yfactor = 1.0 / xfactor;
        }
        else
        {
            yfactor = r.nextDouble() + 0.1;
            xfactor = 1.0 / yfactor;
        }
        mSinXFact = sinangle * xfactor;
        mCosXFact = cosangle * xfactor;
        mSinYFact = sinangle * yfactor;
        mCosYFact = cosangle * yfactor;
    }

    public double value(double x, double y) 
    {
        x += mXOff;
        y += mYOff;
        double x_rot = x * mCosXFact - y * mSinXFact;
        double y_rot = x * mSinYFact + y * mCosYFact;
        return mSource.value(x_rot, y_rot);
    }
}

class Gradientor implements  ImageLayer
{
	RandomSingleton r = RandomSingleton.getInstance();
    pixel mAVal;
    pixel mBVal;
    PlanarWave mSource;

    Gradientor(PlanarWave source,  StarfishPalette colours)
    {
        mSource = source;
        // Pick two different colours from the palette. These will be
        // the endpoints of our gradient.
        int aindex, bindex;
        aindex = (int) (r.nextDouble() * colours.colourCount);
        mAVal = colours.colour[ aindex ];
        do
        {
            bindex = (int) (r.nextDouble() * colours.colourCount);
        }
        while (bindex == aindex);
        mBVal = colours.colour[ bindex ];
    }

    public pixel value(double x, double y) 
    {
        double val = (mSource.value(x, y) + 1.0) / 2.0;
        return new pixel((int) ((mBVal.red - mAVal.red) * val + mAVal.red),
                			 (int) ((mBVal.green - mAVal.green) * val + mAVal.green),
                			 (int) ((mBVal.blue - mAVal.blue) * val + mAVal.blue));

    }
}

class Compositor implements  ImageLayer
{
    ImageLayer mSrcA;
    ImageLayer mSrcB;
    PlanarWave mMask;

    Compositor(ImageLayer a, PlanarWave mask, ImageLayer b)
    {
        mSrcA = a;
        mMask = mask;
        mSrcB = b;
    }

    public pixel value(double x, double y) 
    {
        pixel a = mSrcA.value(x, y);
        pixel b = mSrcB.value(x, y);
        double mask = mMask.value(x, y);
        mask = (mask + 1.0) / 2.0;
        return new pixel((int)((b.red - a.red) * mask + a.red),
                			 (int)((b.green - a.green) * mask + a.green),
                			 (int)((b.blue - a.blue) * mask + a.blue));
    }
}

class AntialiasImage implements  ImageLayer
{
    double mDX;
    double mDY;
    double mSamplesRecip;
    ImageLayer mSource;
    AAMode mMode;

    AntialiasImage(ImageLayer source, double x, double y, AAMode mode)
    {
        mSource = source;
        mDX = 0.5/x;
        mDY = 0.5/y;
        mMode = mode;
        mSamplesRecip = (mMode == AAMode.AAMODE_4X) ? 0.25 : 0.5;
    }

    public pixel value(double x, double y) 
    {
        int red, green, blue;
        pixel oval = mSource.value(x, y);
        red = oval.red;
        green   = oval.green;
        blue    = oval.blue;
        oval = mSource.value(x + mDX, y + mDY);
        red = red + oval.red;
        green   = green + oval.green;
        blue    = blue + oval.blue;
        if (mMode == AAMode.AAMODE_4X)
        {
            oval = mSource.value(x + mDX, y);
            red = red + oval.red;
            green   = green + oval.green;
            blue    = blue + oval.blue;
            oval = mSource.value(x, y + mDY);
            red = red + oval.red;
            green   = green + oval.green;
            blue    = blue + oval.blue;
        }
        oval.red		= (char) (red * mSamplesRecip);
        oval.green	= (char) (green * mSamplesRecip);
        oval.blue	= (char) (blue * mSamplesRecip);
        return oval;
    }
}

class StarfishEngine 
{
    int mWidth;
    int mHeight;
    boolean mWrapEdges;
    ImageLayer mSource;

    StarfishEngine(int width, int height,  StarfishPalette palette, 
            boolean wrapEdges, int complexity, AAMode aamode)
    {
        mWidth = width;
        mHeight = height;
        mWrapEdges = wrapEdges;

        if (mWrapEdges)
        {
            complexity /= 2;
        }

        mSource = newImageLayer(palette, complexity);
        if (aamode != AAMode.AAMODE_NONE)
        {
            mSource = new AntialiasImage(mSource, width, height, aamode);
        }
    }

    LinearWave newLinearWave(int complexity)
    {
    	RandomSingleton r = RandomSingleton.getInstance();
        LinearWave out = null;
        int selector = r.nextInt(3);
        switch (selector)
        {
            case 0: out = new Coswave(); break;
            case 1: out = new Sawtooth(); break;
            case 2: out = new Ess(); break;
        }
        while (complexity > 0)
        {
            selector = r.nextInt(8);
            switch (selector)
            {
                case 0: 
                    complexity--;
                    break;
                case 1:
                    out = new InvertWave(out);
                    complexity--;
                    break;
                case 2: 
                    out = new GammaLinear(out);
                    complexity--;
                    break;
                case 3: 
                    if (complexity >= 2)
                    {
                        out = new InsertWavePeaks(out);
                        complexity -= 2;
                    }
                    break;
                case 4: 
                    out = new Modulator(out, newLinearWave(complexity));
                    complexity = 0;
                    break;
                case 5: 
                    out = new MixLinear(out, newLinearWave(complexity));
                    complexity = 0;
                    break;
                case 6: 
                    out = new MinimaxLinear(out, newLinearWave(complexity));
                    complexity = 0;
                    break;
                case 7: 
                    out = new MultiplyLinear(out, newLinearWave(complexity));
                    complexity = 0;
                    break;
            }
        }
        return out;
    }

    PlanarWave newPlanarWave(int complexity)
    {
    	RandomSingleton r = RandomSingleton.getInstance();
        PlanarWave out = null;
        int selector;

        int modifierComplexity = r.nextInt(complexity + 1);
        int sourceComplexity = complexity - modifierComplexity;
        int subwaveComplexity = 0;
        selector = r.nextInt(5);

        switch (selector)
        {
            case 0: 
            		out = new Pebbledrop(newLinearWave(sourceComplexity)); 
            		break;
            case 1: 
            		out = new Curtain(newLinearWave(sourceComplexity)); 
            		break;
            case 2: 
            		out = new Zigzag(newLinearWave(sourceComplexity / 2), newLinearWave(sourceComplexity / 2)); 
            		break;
            case 3: 
            		out = new Starfish2(newLinearWave(sourceComplexity / 2), newLinearWave(sourceComplexity / 2)); 
            		break;
            case 4: 
            		out = new Spinflake(newLinearWave(sourceComplexity)); 
            		break;
        }
        if (r.nextDouble() >= 0.5)
        {
            out = new InvertPlane(out);
        }
        while (modifierComplexity > 0)
        {
            selector = r.nextInt(10);
            switch (selector)
            {
                case 0:
                    modifierComplexity = modifierComplexity - 1;
                    break;
                case 1:
                    out = new MinimaxPlanar(out, newPlanarWave(modifierComplexity));
                    modifierComplexity = 0;
                    break;
                case 2:
                    out = new MixPlanar(out, newPlanarWave(modifierComplexity));
                    modifierComplexity = 0;
                    break;
                case 3:
                    modifierComplexity = modifierComplexity / 2;
                    out = new WarpPlane(new Mixmaster(out), newLinearWave(modifierComplexity));
                    if (modifierComplexity > 0)
                    {
                        modifierComplexity = modifierComplexity - 1;
                    }
                    break;
                case 4:
                    modifierComplexity = modifierComplexity / 2;
                    out = new Reflector(new Mixmaster(out));
                    break;
                case 5:
                    modifierComplexity = modifierComplexity - 1;
                    out = new GammaPlanar(out);
                    break;
                case 6:
                    out = new MultiplyPlanar(out, newPlanarWave(modifierComplexity));
                    modifierComplexity = 0;
                    break;
                case 7:
                    out = new Quadratesselator(out);
                    modifierComplexity = modifierComplexity / 2;
                    break;
                case 8:
                    out = new Hexatesselator(out);
                    modifierComplexity = modifierComplexity / 2;
                    break;
                case 9:
                    subwaveComplexity = (int) (modifierComplexity * r.nextDouble());
                    out = new Rotawarp(new Mixmaster(out), newLinearWave(subwaveComplexity));
                    modifierComplexity = modifierComplexity - subwaveComplexity;
                    break;
            }
        }
        out = new Mixmaster(out);
        return out;
    }

    ImageLayer newImageLayer( StarfishPalette colours, int complexity)
    {
        RandomSingleton r = RandomSingleton.getInstance();

        if (Math.pow(r.nextDouble(), 4.0) > 1.0 / complexity)
        {
            PlanarWave mask = newPlanarWave(complexity / 4);
            complexity -= (complexity / 4);
            ImageLayer a = newImageLayer(colours, complexity / 2);
            ImageLayer b = newImageLayer(colours, complexity / 2);
            return new Compositor(a, mask, b);
        }
        else
        {
            return new Gradientor(newPlanarWave(complexity), colours);
        }
    }	

    pixel getPixel(int x, int y)
    {
        pixel out = new pixel(0,0,0);
        double fx = (((double)x * 2.0) / (double)mWidth) - 1.0;
        double fy = (((double)y * 2.0) / (double)mHeight) - 1.0;

        if (mWrapEdges)
        {
            double xbackmask = ((double)x) / (double)mWidth;
            double xmask = 1.0 - xbackmask;
            pixel topleft = mSource.value(fx + 1.0, fy);
            pixel topright = mSource.value(fx - 1.0, fy);
            pixel bottomleft = mSource.value(fx + 1.0, fy - 2.0);
            pixel bottomright = mSource.value(fx - 1.0, fy - 2.0);
            pixel top = new pixel(0,0,0); 
            top.red   = (int)((topleft.red * xmask) + (topright.red * xbackmask)) & 0xFF;
            top.green = (int)((topleft.green * xmask) + (topright.green * xbackmask)) & 0xFF;
            top.blue  = (int)((topleft.blue * xmask) + (topright.blue * xbackmask)) & 0xFF;
            pixel bottom = new pixel(0,0,0);
            bottom.red = (int)((bottomleft.red * xmask) + (bottomright.red * xbackmask)) & 0xFF;
            bottom.green = (int)((bottomleft.green * xmask) + (bottomright.green * xbackmask)) & 0xFF;
            bottom.blue = (int)((bottomleft.blue * xmask) + (bottomright.blue * xbackmask)) & 0xFF;
            double ybackmask = ((double)y) /(double)(mHeight);
            double ymask = 1.0 - ybackmask;
            out.red   = (int)((top.red * ymask) + (bottom.red * ybackmask)) & 0xFF;
            out.green = (int)((top.green * ymask) + (bottom.green * ybackmask)) & 0xFF;
            out.blue  = (int)((top.blue * ymask) + (bottom.blue * ybackmask)) & 0xFF;
        }
        else
        {
            out = mSource.value(fx, fy);
        }
        return out;
    }


    static StarfishPalette initRandomPalette(StarfishPalette p)
    {
        RandomSingleton r = RandomSingleton.getInstance();

        p.colourCount = r.nextInt(255) + 2;

        p.colour[0] = new pixel(0,0,0);
        p.colour[1] = new pixel(255,255,255);

        for (int i = 2; i < p.colourCount; i++)
        {
            p.colour[i] = new pixel(r.nextInt(256),r.nextInt(256),r.nextInt(256));
        }
        return p;
    }
}

class DisplayWindow extends JDialog
{
    private JPanel panel = new JPanel();
    private JFrame mainFrame = new JFrame();
    private JButton saveButton = new JButton("Save");
    private BufferedImage bi;
    private Thread runThread;

    DisplayWindow(final StarfishEngine sfe, final int width, final int height)
    {
        saveButton.addActionListener(new ActionListener()
        {			
            public void actionPerformed(ActionEvent evt)
            {
                JFileChooser fc = new JFileChooser();
                PathSingleton.getInstance();
                File f = PathSingleton.getPath();
                if (!f.toString().equals("")) fc.setCurrentDirectory(f);
                fc.setDialogTitle("Whither Thou Goest...");
                fc.setFileFilter(new FileFilter()
                {        	
                    public boolean accept(File f)
                    {
                        return f.getName().toLowerCase().endsWith(".png") || f.isDirectory();
                    }			
                    public String getDescription()
                    {
                        return "StarFish";
                    }
                });
                DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
                String ts = dateFormat.format(new Date());
                fc.setSelectedFile(new File("starfish-" + ts + ".png"));
                int returnVal = fc.showSaveDialog(mainFrame);
                if (returnVal == JFileChooser.APPROVE_OPTION)
                {
                    try 
                    {
                        ImageIO.write(bi, "png", fc.getSelectedFile());
                        mainFrame.dispose();
                        PathSingleton.setPath(fc.getCurrentDirectory());
                    } 
                    catch (IOException e) 
                    {
                        System.out.println("No Starfish for you!");
                    }
                }
            }
        });
        saveButton.setEnabled(false);

        bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        final JLabel picLabel = new JLabel(new ImageIcon(bi));
        Border border = LineBorder.createGrayLineBorder();
        picLabel.setBorder(border);

        panel.setPreferredSize(new Dimension(width, height));
        panel.setLayout(new BoxLayout(panel,BoxLayout.Y_AXIS));
        panel.add(picLabel);
        panel.add(saveButton);
        mainFrame.getContentPane().add(panel);
        mainFrame.setTitle("JStarfish");
        mainFrame.setMinimumSize(new Dimension(Math.max(100,width+5), height+70));
        mainFrame.setPreferredSize(new Dimension(width+5, height+70));
        mainFrame.setLocation(240, 40);
        mainFrame.setVisible(true);

        runThread = new Thread(new Runnable() 
        {
            public void run() 
            {
                for (int i=0; i < width; i++)
                {
                    for (int j = 0; j < height; j++)
                    {
                        pixel p = sfe.getPixel(i,j);
                        int c = (p.red * 65536) + (p.green * 256) + p.blue;
                        bi.setRGB(i, j, c); 
                    }
                    if (!mainFrame.isVisible()) 
                    {
                        runThread.interrupt();
                        break;
                    }
                    picLabel.repaint();
                }
                saveButton.setEnabled(true);
            }
        }, "JStarFish");
        runThread.start();
    }
}

public class Starfish extends JDialog
{
    String width[] = {"64", "128", "256", "320", "400", "512", "640", "1024", "1280"};
    String height[] = {"64", "128", "256", "384", "400", "512", "768", "800", "1200", "1600"};
    String wrapEdges[] = {"True", "False"};
    String aamode[] = {"None", "x2", "x4"};
    String complexity[] = {"10", "20", "30", "40", "50", "60", "70", "80", "90", "100"};

    private JComboBox widthBox = new JComboBox(width);
    private JComboBox heightBox = new JComboBox(height);
    private JComboBox paletteBox = new JComboBox();
    private JComboBox wrapEdgesBox = new JComboBox(wrapEdges);
    private JComboBox complexityBox = new JComboBox(complexity);
    private JComboBox aamodeBox = new JComboBox(aamode);

    private JLabel widthLabel = new JLabel("Width:", SwingConstants.LEFT);
    private JLabel heightLabel = new JLabel("Height:", SwingConstants.LEFT);
    private JLabel paletteLabel = new JLabel("Palette:", SwingConstants.LEFT);
    private JLabel wrapEdgesLabel = new JLabel("Wrap Edges:", SwingConstants.LEFT);
    private JLabel complexityLabel = new JLabel("Complexity:", SwingConstants.LEFT);
    private JLabel aamodeLabel = new JLabel("Anti-Alias Mode:", SwingConstants.LEFT);
    private JLabel dummyLabel = new JLabel();

    private JButton goButton = new JButton("Go!");
    private JButton randomButton = new JButton("Randomize Parameters");
    private JButton randPaletteButton = new JButton("Randomize Palette");

    private JFrame mainFrame = new JFrame();
    private JPanel panel = new JPanel();

    private StarfishPalette palettes[] = new StarfishPalette[256];
    int numberOfPalettes;
    int numberOfColors;

    public static void main(String[] args)
    {
    	System.setProperty("apple.awt.application.appearance", "system");
		System.setProperty( "apple.awt.application.name", "JStarfish" );
        try
        {
        		UIManager.setLookAndFeel(new NimbusLookAndFeel());
        }
        catch (Exception e)
        {
            System.out.println("Starfish cannot see the Clouds.");
        }
        new Starfish();
    }

    Starfish()
    {		
        try
        {
            paletteBox.removeAllItems();
            InputStream is = getClass().getResourceAsStream("/resource/palettes.txt");
            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(isr);
            String w = "";
            numberOfPalettes = 0;
            numberOfColors = 0;
            while ((w = br.readLine()) != null) 
            {       					        	
                if (!w.equals(""))
                {
                    if (w.startsWith("#"))
                    {	            				            			
                        paletteBox.addItem(w.substring(1));
                        palettes[numberOfPalettes] = new StarfishPalette();
                    }
                    else if (w.startsWith("="))
                    {
                        palettes[numberOfPalettes].colourCount = numberOfColors;
                        numberOfColors = 0;
                        numberOfPalettes++;
                    }
                    else
                    {
                        int c = Integer.parseInt(w, 16);	
                        int r = (c >>> 16) & 0xFF;	            			
                        int g = (c >>> 8) & 0xFF;
                        int b = (c) & 0xFF;
                        palettes[numberOfPalettes].colour[numberOfColors] = new pixel(r,g,b);	            			
                        numberOfColors++;
                    }
                }
            }
            br.close();
        }
        catch (Exception ex)
        {
            System.out.println("Your Starfish is transparent.");
        }

        randomButton.addActionListener(new ActionListener()
        {			
            public void actionPerformed(ActionEvent evt)
            {
                final RandomSingleton r = RandomSingleton.getInstance();
                int rn = r.nextInt(widthBox.getItemCount());
                widthBox.setSelectedIndex(rn);
                rn = r.nextInt(heightBox.getItemCount());
                heightBox.setSelectedIndex(rn);
                rn = r.nextInt(paletteBox.getItemCount());
                paletteBox.setSelectedIndex(rn);
                rn = r.nextInt(wrapEdgesBox.getItemCount());
                wrapEdgesBox.setSelectedIndex(rn);
                rn = r.nextInt(complexityBox.getItemCount());
                complexityBox.setSelectedIndex(rn);
                rn = r.nextInt(aamodeBox.getItemCount());
                aamodeBox.setSelectedIndex(rn);
            }
        });

        goButton.addActionListener(new ActionListener()
        {			
            public void actionPerformed(ActionEvent evt)
            {
                int width = Integer.parseInt((String) widthBox.getSelectedItem());
                int height = Integer.parseInt((String) heightBox.getSelectedItem());
                StarfishPalette palette = new StarfishPalette();
                StarfishPalette p = palettes[paletteBox.getSelectedIndex()];
                palette.colourCount = p.colourCount;
                for (int i = 0; i < palette.colourCount; i++)
                {
                    palette.colour[i] = new pixel(p.colour[i].red,p.colour[i].green,p.colour[i].blue);
                }
                boolean wrapEdges = true;
                if ((heightBox.getSelectedItem()).equals("False")) wrapEdges = false;
                int complexity = Integer.parseInt((String) complexityBox.getSelectedItem());
                int aa = aamodeBox.getSelectedIndex();
                AAMode aamode = AAMode.AAMODE_NONE;
                if (aa == 1) aamode = AAMode.AAMODE_2X;
                else if (aa == 2) aamode = AAMode.AAMODE_4X;				
                StarfishEngine sfe = new StarfishEngine(width, height, palette, wrapEdges, complexity, aamode);
                new DisplayWindow(sfe, width, height);
            }
        });

        randPaletteButton.addActionListener(new ActionListener()
        {			
            public void actionPerformed(ActionEvent evt)
            {
                paletteBox.setSelectedItem("Random");
                StarfishEngine.initRandomPalette(palettes[paletteBox.getSelectedIndex()]);
            }
        });

        ((JLabel)widthBox.getRenderer()).setHorizontalAlignment(JLabel.RIGHT);
        ((JLabel)heightBox.getRenderer()).setHorizontalAlignment(JLabel.RIGHT);
        ((JLabel)paletteBox.getRenderer()).setHorizontalAlignment(JLabel.RIGHT);
        ((JLabel)wrapEdgesBox.getRenderer()).setHorizontalAlignment(JLabel.RIGHT);
        ((JLabel)complexityBox.getRenderer()).setHorizontalAlignment(JLabel.RIGHT);
        ((JLabel)aamodeBox.getRenderer()).setHorizontalAlignment(JLabel.RIGHT);

        panel.setLayout(new GridLayout(0,1));
        panel.add(widthLabel);
        panel.add(widthBox);
        panel.add(heightLabel);
        panel.add(heightBox);
        panel.add(paletteLabel);
        panel.add(paletteBox);
        panel.add(wrapEdgesLabel);
        panel.add(wrapEdgesBox);
        panel.add(complexityLabel);
        panel.add(complexityBox);
        panel.add(aamodeLabel);
        panel.add(aamodeBox);
        panel.add(dummyLabel);
        panel.add(randomButton);
        panel.add(randPaletteButton);
        panel.add(goButton);

        mainFrame.getContentPane().add(panel);
        mainFrame.setTitle("JStarfish");
        mainFrame.setMinimumSize(new Dimension(200, 420));
        mainFrame.setPreferredSize(new Dimension(200, 420));
        mainFrame.setMaximumSize(new Dimension(200, 420));
        mainFrame.setLocation(40, 40);
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.setResizable(false);
        mainFrame.setVisible(true);
    }
}
