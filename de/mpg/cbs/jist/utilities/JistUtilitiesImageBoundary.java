package de.mpg.cbs.jist.utilities;

import edu.jhu.ece.iacl.jist.pipeline.AlgorithmInformation;
import edu.jhu.ece.iacl.jist.pipeline.AlgorithmInformation.AlgorithmAuthor;
import edu.jhu.ece.iacl.jist.pipeline.AlgorithmInformation.Citation;
import edu.jhu.ece.iacl.jist.pipeline.AlgorithmRuntimeException;
import edu.jhu.ece.iacl.jist.pipeline.CalculationMonitor;
import edu.jhu.ece.iacl.jist.pipeline.DevelopmentStatus;
import edu.jhu.ece.iacl.jist.pipeline.ProcessingAlgorithm;
import edu.jhu.ece.iacl.jist.pipeline.parameter.ParamCollection;
import edu.jhu.ece.iacl.jist.pipeline.parameter.ParamOption;
import edu.jhu.ece.iacl.jist.pipeline.parameter.ParamVolume;
import edu.jhu.ece.iacl.jist.pipeline.parameter.ParamInteger;
import edu.jhu.ece.iacl.jist.structures.image.ImageData;
import edu.jhu.ece.iacl.jist.structures.image.ImageDataFloat;
import edu.jhu.ece.iacl.jist.structures.image.ImageDataMipav;

import de.mpg.cbs.libraries.*;
import de.mpg.cbs.utilities.*;

/*
 * @author Pierre-Louis bazin (bazin@cbs.mpg.de)
 *
 */
public class JistUtilitiesImageBoundary extends ProcessingAlgorithm{
	ParamVolume volParam;
	ParamVolume resultVolParam;
	ParamOption operationParam;
	ParamInteger distParam;
	
	private static final String cvsversion = "$Revision: 1.10 $";
	private static final String revnum = cvsversion.replace("Revision: ", "").replace("$", "").replace(" ", "");
	private static final String shortDescription = "Sets the values at the image boundary to specific values";
	private static final String longDescription = "(we can use zero, min, max)";

	private static final byte ZERO = 0;
	private static final byte MIN = 1;
	private static final byte MAX = 2;

	protected void createInputParameters(ParamCollection inputParams) {
		inputParams.add(volParam=new ParamVolume("Image Volume"));
		inputParams.add(operationParam=new ParamOption("Image boundary value",new String[]{"zero","min","max"}));
		inputParams.add(distParam=new ParamInteger("Image boundary size (voxels)", 1, 100, 1));
		
		inputParams.setPackage("CBS Tools");
		inputParams.setCategory("Utilities");
		inputParams.setLabel("Set Image Boundary");
		inputParams.setName("SetImageBoundary");

		AlgorithmInformation info = getAlgorithmInformation();
		info.setWebsite("http://www.cbs.mpg.de/");
		info.setDescription(shortDescription);
		info.setLongDescription(shortDescription + longDescription);
		info.setVersion(revnum);
		info.setEditable(false);
		info.setStatus(DevelopmentStatus.RC);
	}


	protected void createOutputParameters(ParamCollection outputParams) {
		outputParams.add(resultVolParam=new ParamVolume("Result Volume",null,-1,-1,-1,-1));
	}


	protected void execute(CalculationMonitor monitor) throws AlgorithmRuntimeException {
		ImageDataFloat vol = new ImageDataFloat(volParam.getImageData());
		int nx=vol.getRows();
		int ny=vol.getCols();
		int nz=vol.getSlices();
		int nt=vol.getComponents();
		
		ImageDataFloat resultData;
		
		if (nt==1) {
			float[][][] image = vol.toArray3d();
			
			// main algorithm
			float[][][] result = new float[nx][ny][nz];
			
			float Imin = ImageStatistics.minimum(image,nx,ny,nz);
			float Imax = ImageStatistics.maximum(image,nx,ny,nz);
			int d = distParam.getValue().intValue();
					
			byte op = ZERO;
			if (operationParam.getValue().equals("min")) op = MIN;
			else if (operationParam.getValue().equals("max")) op = MAX;
			
			for (int x=0;x<nx;x++) for (int y=0;y<ny;y++) for (int z=0;z<nz;z++) {
				if (x<d || x>=nx-d || y<d || y>=ny-d || z<d || z>=nz-d) {
					if (op==ZERO) result[x][y][z] = 0.0f;
					else if (op==MIN) result[x][y][z] = Imin;
					else if (op==MAX) result[x][y][z] = Imax;
				} else {
					result[x][y][z] = image[x][y][z];
				}
			}
			resultData = new ImageDataFloat(result);	
		} else {
			float[][][][] image = vol.toArray4d();
			
			// main algorithm
			float[][][][] result = new float[nx][ny][nz][nt];
			
			float Imin = ImageStatistics.minimum(image,nx,ny,nz,nt);
			float Imax = ImageStatistics.maximum(image,nx,ny,nz,nt);
			int d = distParam.getValue().intValue();
					
			byte op = ZERO;
			if (operationParam.getValue().equals("min")) op = MIN;
			else if (operationParam.getValue().equals("max")) op = MAX;
			
			for (int x=0;x<nx;x++) for (int y=0;y<ny;y++) for (int z=0;z<nz;z++) for (int t=0;t<nt;t++) {
				if (x<d || x>=nx-d || y<d || y>=ny-d || z<d || z>=nz-d) {
					if (op==ZERO) result[x][y][z][t] = 0.0f;
					else if (op==MIN) result[x][y][z][t] = Imin;
					else if (op==MAX) result[x][y][z][t] = Imax;
				} else {
					result[x][y][z][t] = image[x][y][z][t];
				}
			}
			resultData = new ImageDataFloat(result);	
		}
		resultData.setHeader(vol.getHeader());
		resultData.setName(vol.getName()+"_imb");
		resultVolParam.setValue(resultData);
		resultData = null;
	}
}
