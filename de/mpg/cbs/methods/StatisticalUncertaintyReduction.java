package de.mpg.cbs.methods;

import java.io.*;
import java.util.*;
import gov.nih.mipav.view.*;

import gov.nih.mipav.model.structures.jama.*;

import de.mpg.cbs.libraries.*;
import de.mpg.cbs.structures.*;
import de.mpg.cbs.utilities.*;

import org.apache.commons.math3.util.FastMath;

/**
 *
 *  This algorithm uses an image-based diffusion technique to reduce uncertainty
 *	in multi-atlas or statistical labelings
 *
 *	@version    August 2016
 *	@author     Pierre-Louis Bazin 
 *		
 *
 */
 
public class StatisticalUncertaintyReduction {
	
	
	// image data
	private 	float[][]		image;  			// original images
	private 	int				nix,niy,niz, nxyzi;   		// image dimensions
	//private 	float			rix,riy,riz;   		// image resolutions
	private		boolean[]		imused;				// check if image modality / contrast is used
	private		float[]			imscale;			// image intensity scaling
	private		float[][]			imvar;			// image intensity scaling
	private		int				nc;					// number of channels

	// labeling parameters
	private 	int 			nobj;    			// number of shapes
	private 	int[]			objlabel;			// label values in the original image
	private		float[][]		bestproba;			// best probability function
	private		byte[][]		bestlabel;			// corresponding labels
	private static	byte 	  	nbest;				// total number of probability functions
	//private 	byte[] 			segmentation;   	// MGDM's segmentation (object indices 1 to N)
	private		boolean[]		mask;				// masking regions not used in computations
	
	// for debug and display
	private static final boolean		debug=false;
	private static final boolean		verbose=true;
	
	/**
	 *  constructors for different cases: with/out outliers, with/out selective constraints
	 */
	public StatisticalUncertaintyReduction(float[][] img_, boolean[] used_, float[] sca_, int nc_,
									int nix_, int niy_, int niz_, 
									//float rix_, float riy_, float riz_,
									byte nbest_) {
		this(img_, null, used_, sca_, nc_, nix_, niy_, niz_, nbest_);
	}
	public StatisticalUncertaintyReduction(float[][] img_, float[][] var_, boolean[] used_, float[] sca_, int nc_,
									int nix_, int niy_, int niz_, 
									//float rix_, float riy_, float riz_,
									byte nbest_) {
	
		image = img_;
		imscale = sca_;
		imvar = var_;
		imused = used_;
		nc = nc_;
		nix = nix_;
		niy = niy_;
		niz = niz_;
		nxyzi = nix*niy*niz;
		//rix = rix_;
		//riy = riy_;
		//riz = riz_;
		nbest = nbest_;
		
		// init all the arrays in atlas space
		try {
			//segmentation = new byte[nix*niy*niz];	
			mask = new boolean[nix*niy*niz];	
		} catch (OutOfMemoryError e){
			 finalize();
			System.out.println(e.getMessage());
			return;
		}
		if (debug) BasicInfo.displayMessage("initial probability decomposition\n");		
		
		// basic mask: remove two layers off the images (for avoiding limits)
		for (int x=0; x<nix; x++) for (int y=0; y<niy; y++) for (int z = 0; z<niz; z++) {
			int xyzi = x+nix*y+nix*niy*z;
			mask[xyzi] = false;
			if (x>0 && x<nix-1 && y>0 && y<niy-1 && z>0 && z<niz-1) {
				for (int c=0;c<nc;c++) if (image[c][xyzi]!=0) mask[xyzi] = true;	
			}
		}
	}
	
	public void finalize() {
		//segmentation = null;
	}
	
	public final void setBestProbabilities(float[][] bestpb, byte[][] bestlb, int[] objlb) {
		bestproba = bestpb;
		bestlabel = bestlb;
		nbest = Numerics.min(nbest, (byte)bestproba.length);
		objlabel = objlb;
		nobj = objlabel.length;
	}
	
	public final void initBestProbaFromSegmentation(int[] seg, int nlb, float dist) {
		bestlabel = new byte[nbest][nxyzi];
		nobj = nlb;
		objlabel = new int[nobj];
		
		// set the first labels
		byte nset=0;
		for (int xyzi=0; xyzi<nxyzi; xyzi++) if (mask[xyzi]) {
			byte lb=-1;
			for (byte n=0;n<nset && lb==-1;n++) if (seg[xyzi]==objlabel[n]) lb=n;
			if (lb==-1) {
				lb = nset;
				objlabel[nset] = seg[xyzi];
				nset++;
			}
			bestlabel[0][xyzi] = lb;
		}
		
		// propagate the distances MGDM-style
		// ...
	}
	
	public final float[][] getProbabilities() { return bestproba; }
	
	public final byte[][] getLabels() { return bestlabel; }
	
	//public final byte[] getSegmentation() { return segmentation; }
    
	public final float[] computeMaxImageWeight(float scale) {
		float[] imgweight = new float[nix*niy*niz];   	
		
		for (int x=1;x<nix-1;x++) for (int y=1;y<niy-1;y++) for (int z=1;z<niz-1;z++) {
			int xyzi = x+nix*y+nix*niy*z;
			imgweight[xyzi] = 0.0f;
			for (byte j=0;j<26;j++) {
				int xyzj = Ngb.neighborIndex(j, xyzi, nix, niy, niz);
				imgweight[xyzi] = Numerics.max(imgweight[xyzi], diffusionImageWeightFunction(xyzi,xyzj,scale));
			}
		}
		return imgweight;
	}
	public final float[] computeMinImageWeight(float scale) {
		float[] imgweight = new float[nix*niy*niz];   	
		
		for (int x=1;x<nix-1;x++) for (int y=1;y<niy-1;y++) for (int z=1;z<niz-1;z++) {
			int xyzi = x+nix*y+nix*niy*z;
			imgweight[xyzi] = 1.0f;
			for (byte j=0;j<26;j++) {
				int xyzj = Ngb.neighborIndex(j, xyzi, nix, niy, niz);
				imgweight[xyzi] = Numerics.min(imgweight[xyzi], diffusionImageWeightFunction(xyzi,xyzj,scale));
			}
		}
		return imgweight;
	}
	public final float[][] computeAllImageWeight(float scale) {
		float[][] imgweight = new float[26][nix*niy*niz];   	
		
		for (int x=1;x<nix-1;x++) for (int y=1;y<niy-1;y++) for (int z=1;z<niz-1;z++) {
			int xyzi = x+nix*y+nix*niy*z;
			for (byte j=0;j<26;j++) {
				int xyzj = Ngb.neighborIndex(j, xyzi, nix, niy, niz);
				imgweight[j][xyzi] = diffusionImageWeightFunction(xyzi,xyzj,scale);
			}
		}
		return imgweight;
	}
	public final float[][] computeBestImageWeight(float scale, int ngbsize) {
		float[][] imgweight = new float[ngbsize][nix*niy*niz];   	
		
		float[] w0 = new float[26];
		for (int x=1;x<nix-1;x++) for (int y=1;y<niy-1;y++) for (int z=1;z<niz-1;z++) {
			int xyzi = x+nix*y+nix*niy*z;
			for (byte j=0;j<26;j++) {
				int xyzj = Ngb.neighborIndex(j, xyzi, nix, niy, niz);
				w0[j] = diffusionImageWeightFunction(xyzi,xyzj,scale);
			}
			byte[] rank = Numerics.argmax(w0, ngbsize);
			for (byte n=0;n<ngbsize;n++) {
				imgweight[n][xyzi] = w0[rank[n]];
			}
		}
		return imgweight;
	}
	public final float[] computeMaxCertainty(float factor) {
		float[] certainty = new float[nix*niy*niz];   	
		
		for (int xyzi=0;xyzi<nix*niy*niz;xyzi++) if (mask[xyzi]) {
			certainty[xyzi] = certaintyFunction(bestproba[0][xyzi]-bestproba[1][xyzi],factor);
		}
		return certainty;	
	}
	
    public final void diffuseCertainty(int iter, float scale, float factor, int ngbsize, float mincertainty, boolean computeDistribution) {
    	
		//mix with the neighbors?
		float[] certainty = new float[nix*niy*niz];   	
		float[][] newproba = new float[nbest][nix*niy*niz];
		byte[][] newlabel = new byte[nbest][nix*niy*niz];
		float[] ngbweight = new float[26];
		float[][] imgweight = new float[nix*niy*niz][26];   	
		//byte[][] mapdepth = new byte[nobj][nix*niy*niz];	
		byte[] mapdepth = new byte[nix*niy*niz];	
		
		float[][] objmean = new float[nobj][nc];
		float[][] objvar = new float[nobj][nc];
		float[] objcount = new float[nobj];
		
		// SOR-scheme?
		//float sorfactor = 1.95f;
		float sorfactor = 1.0f;
		
		// compute the functional factor
		//float certaintyfactor = (float)(FastMath.log(0.5)/FastMath.log(factor));
		float certaintyfactor = factor;
		BasicInfo.displayMessage("certainty exponent "+certaintyfactor+"\n");
		
		// rescale the certainty threshold so that it maps to the computed certainty values
		mincertainty = certaintyFunction(mincertainty,certaintyfactor);
		BasicInfo.displayMessage("minimum certainty threshold"+mincertainty+"\n");
		
		for (int x=1;x<nix-1;x++) for (int y=1;y<niy-1;y++) for (int z=1;z<niz-1;z++) {
			int xyzi = x+nix*y+nix*niy*z;
			for (byte j=0;j<26;j++) {
				int xyzj = Ngb.neighborIndex(j, xyzi, nix, niy, niz);
				imgweight[xyzi][j] = sorfactor*diffusionImageWeightFunction(xyzi,xyzj,scale)/ngbsize;
			}
		}
		for (int m=0;m<nbest;m++) for (int xyzi=0;xyzi<nix*niy*niz;xyzi++) {
			newproba[m][xyzi] = bestproba[m][xyzi];
			newlabel[m][xyzi] = bestlabel[m][xyzi];
		}
		/* redo every time?? 
		for (int xyzi=0;xyzi<nix*niy*niz;xyzi++) {
			for (byte n=0;n<nobj;n++) {
				mapdepth[n][xyzi] = nbest;
				for (byte m=0;m<nbest;m++) {
					if (bestlabel[m][xyzi]==n) {
						mapdepth[n][xyzi] = m;
					}
				}
			}
		}
		*/
		
		float maxdiff = 1.0f;
		float meandiff = 1.0f;
		for (int t=0;t<iter && meandiff>0.0005f;t++) {
		//for (int t=0;t<iter && maxdiff>0.1f;t++) {
			BasicInfo.displayMessage("iter "+(t+1));
			maxdiff = 0.0f;
			meandiff = 0.0f;
			float ndiff = 0.0f;
			float nflip = 0.0f;
			float nproc = 0.0f;
			/*
			// re-compute depth
			for (int xyzi=0;xyzi<nix*niy*niz;xyzi++) {
				for (byte n=0;n<nobj;n++) {
					mapdepth[n][xyzi] = nbest;
					for (byte m=0;m<nbest;m++) {
						if (bestlabel[m][xyzi]==n) {
							mapdepth[n][xyzi] = m;
						}
					}
				}
			}
			*/
			/*
			// estimate a gaussian intensity distribution for each region, and modulate the corresponding labels
			if (computeDistribution) {
				for (int n=0;n<nobj;n++) {
					for (int c=0;c<nc;c++) if (imused[c]) {
						objmean[n][c] = 0.0f;
						objvar[n][c] = 0.0f;
					}
					objcount[n] = 0.0f;
				}
				for (int xyzi=0;xyzi<nix*niy*niz;xyzi++) if (mask[xyzi]) {
					int n = bestlabel[0][xyzi];
					if (n>-1) {
						for (int c=0;c<nc;c++) if (imused[c]) {
							objmean[n][c] += bestproba[0][xyzi]*image[c][xyzi];
						}
						objcount[n]+=bestproba[0][xyzi];
					}
				}
				for (int n=0;n<nobj;n++) if (objcount[n]>0) {
					for (int c=0;c<nc;c++) if (imused[c]) {
						objmean[n][c] /= objcount[n];
					}
				}
				for (int xyzi=0;xyzi<nix*niy*niz;xyzi++) if (mask[xyzi]) {
					int n = bestlabel[0][xyzi];
					if (n>-1) {
						for (int c=0;c<nc;c++) if (imused[c]) {
							objvar[n][c] += bestproba[0][xyzi]*(image[c][xyzi]-objmean[n][c])*(image[c][xyzi]-objmean[n][c]);
						}
					}
				}
				for (int n=0;n<nobj;n++) if (objcount[n]>0) {
					for (int c=0;c<nc;c++) if (imused[c]) {
						objvar[n][c] /= objcount[n];
					}
				}
				for (int xyzi=0;xyzi<nix*niy*niz;xyzi++) if (mask[xyzi]) {
					for (int m=0;m<nbest;m++) {
						int n = bestlabel[m][xyzi];
						if (n>-1) {
							float proba = 1.0f;
							for (int c=0;c<nc;c++) if (imused[c]) {
								float probac = (float)FastMath.exp(0.5*(image[c][xyzi]-objmean[n][c])*(image[c][xyzi]-objmean[n][c])/objvar[n][c]);
								if (probac<proba) proba = probac;
							}
							bestproba[m][xyzi] *= proba;
						}
					}
				}
				// re-sort the gain functions
				for (int xyzi=0;xyzi<nix*niy*niz;xyzi++) {
					for (int m=0;m<nbest-1;m++) {
						boolean stop=true;
						for (int l=nbest-1;l>m;l--) {
							if (bestproba[l][xyzi]>bestproba[l-1][xyzi]) {
								float swap = bestproba[l-1][xyzi];
								bestproba[l-1][xyzi] = bestproba[l][xyzi];
								bestproba[l][xyzi] = swap;
								byte swaplb = bestlabel[l-1][xyzi];
								bestlabel[l-1][xyzi] = bestlabel[l][xyzi];
								bestlabel[l][xyzi] = swaplb;
								stop=false;
							}
						}
						if (stop) m=nbest;
					}
				}
			}*/
				
			// main loop: label-per-label
			for (byte n=0;n<nobj;n++) {
				
				// re-compute depth
				// mapdepth only needed for obj n: single map, recomputed every time?
				for (int xyzi=0;xyzi<nix*niy*niz;xyzi++) {
					mapdepth[xyzi] = nbest;
					for (byte m=0;m<nbest;m++) {
						if (bestlabel[m][xyzi]==n) {
							mapdepth[xyzi] = m;
						}
					}
				}
				
				//BasicInfo.displayMessage("propagate gain for label "+n+"\n");
				BasicInfo.displayMessage(".");

				// get the gain ; normalize
				for (int xyzi=0;xyzi<nix*niy*niz;xyzi++) if (mask[xyzi]) {
					if (mapdepth[xyzi]<nbest) {
						if (mapdepth[xyzi]==0) certainty[xyzi] = certaintyFunction(bestproba[0][xyzi]-bestproba[1][xyzi],certaintyfactor);
						else certainty[xyzi] = certaintyFunction(bestproba[0][xyzi]-bestproba[mapdepth[xyzi]][xyzi],certaintyfactor);
					} else {
						certainty[xyzi] = certaintyFunction(bestproba[0][xyzi]-bestproba[nbest-1][xyzi],certaintyfactor);
					}
				}
				// propagate the values : diffusion
				for (int xyzi=0;xyzi<nix*niy*niz;xyzi++) if (mask[xyzi]) {
				//for (int x=1;x<nix-1;x++) for (int y=1;y<niy-1;y++) for (int z=1;z<niz-1;z++) {
					//int xyzi = x+nix*y+nix*niy*z;
					if (mapdepth[xyzi]<nbest && certainty[xyzi]<=mincertainty) {
						nproc++;
						
						float den = certainty[xyzi];
						float num = den*bestproba[mapdepth[xyzi]][xyzi];
						float prev = bestproba[mapdepth[xyzi]][xyzi];
					
						for (byte j=0;j<26;j++) {
							int xyzj = Ngb.neighborIndex(j, xyzi, nix, niy, niz);
							ngbweight[j] = certainty[xyzj]*imgweight[xyzi][j];
						}
						byte[] rank = Numerics.argmax(ngbweight, ngbsize);
						for (int l=0;l<ngbsize;l++) {
							int xyzl = Ngb.neighborIndex(rank[l], xyzi, nix, niy, niz);
							if (mapdepth[xyzl]<nbest) num += ngbweight[rank[l]]*bestproba[mapdepth[xyzl]][xyzl];
							else num += ngbweight[rank[l]]*bestproba[nbest-1][xyzl];
							den += ngbweight[rank[l]];
						}
						if (den>1e-9f) num /= den;
						
						newproba[mapdepth[xyzi]][xyzi] = num;
						newlabel[mapdepth[xyzi]][xyzi] = n;
						
						meandiff += Numerics.abs(num-prev);
						ndiff++;
						maxdiff = Numerics.max(maxdiff, Numerics.abs(num-prev));
						if (prev<0.5f && num>0.5f) nflip++;
						if (prev>0.5f && num<0.5f) nflip++;
					}
				}
			}
			if (ndiff>0) meandiff /= ndiff;
			// make a hard copy
			for (int m=0;m<nbest;m++) for (int xyzi=0;xyzi<nix*niy*niz;xyzi++) {
				bestproba[m][xyzi] = newproba[m][xyzi];
				bestlabel[m][xyzi] = newlabel[m][xyzi];
			}
			float nresort=0.0f;
			float nreseg=0.0f;
			// re-sort the gain functions
			for (int xyzi=0;xyzi<nix*niy*niz;xyzi++) {
				for (int m=0;m<nbest-1;m++) {
					boolean stop=true;
					for (int l=nbest-1;l>m;l--) {
						if (bestproba[l][xyzi]>bestproba[l-1][xyzi]) {
							float swap = bestproba[l-1][xyzi];
							bestproba[l-1][xyzi] = bestproba[l][xyzi];
							bestproba[l][xyzi] = swap;
							byte swaplb = bestlabel[l-1][xyzi];
							bestlabel[l-1][xyzi] = bestlabel[l][xyzi];
							bestlabel[l][xyzi] = swaplb;
							stop=false;
							nresort++;
							if (l==0) nreseg++;
						}
					}
					//if (stop) m=nbest;
				}
			}
			BasicInfo.displayMessage("mean diff. "+meandiff+", max diff. "+maxdiff+", changed. "+nreseg+"\n");
			//BasicInfo.displayMessage("n processed "+nproc+", n flipped "+nflip+"\n");
			
			//BasicInfo.displayMessage("n resorted"+nresort+"\n");
			
		}
		newproba = null;
		newlabel = null;
		/* every time?
		// re-sort the gain functions
		for (int xyzi=0;xyzi<nix*niy*niz;xyzi++) {
			for (int m=0;m<nbest-1;m++) {
				boolean stop=true;
				for (int l=nbest-1;l>m;l--) {
					if (bestproba[l][xyzi]>bestproba[l-1][xyzi]) {
						float swap = bestproba[l-1][xyzi];
						bestproba[l-1][xyzi] = bestproba[l][xyzi];
						bestproba[l][xyzi] = swap;
						byte swaplb = bestlabel[l-1][xyzi];
						bestlabel[l-1][xyzi] = bestlabel[l][xyzi];
						bestlabel[l][xyzi] = swaplb;
						stop=false;
					}
				}
				if (stop) m=nbest;
			}
		}
		*/
    }
       
    private final float diffusionImageWeightFunction(int xyz, int ngb, float scale) {
    	float maxdiff = 0.0f;
    	if (imvar==null) {
		for (int c=0;c<nc;c++) if (imused[c]) {
			float diff = Numerics.abs((image[c][xyz] - image[c][ngb])/imscale[c]);
			if (diff>maxdiff) maxdiff = diff;
		}
	} else {
		for (int c=0;c<nc;c++) if (imused[c]) {
			float diff = Numerics.abs((image[c][xyz] - image[c][ngb])/imvar[c][xyz]);
			if (diff>maxdiff) maxdiff = diff;
		}
	}
    	return 1.0f/(1.0f+Numerics.square( maxdiff/scale));
    }
    
    private final float certaintyFunction(float delta, float scale) {
    	//return 1.0f - 1.0f/(1.0f+Numerics.square(delta/scale));	
    	return (float)FastMath.pow(Numerics.abs(delta), scale);	
    }
    
}

