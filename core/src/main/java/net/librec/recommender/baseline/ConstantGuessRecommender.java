// Copyright (C) 2014 Guibing Guo
//
// This file is part of LibRec.
//
// LibRec is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// LibRec is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with LibRec. If not, see <http://www.gnu.org/licenses/>.
//

package net.librec.recommender.baseline;

import net.librec.common.LibrecException;
import net.librec.recommender.MatrixRecommender;

/**
 * Baseline: predict by a constant rating
 */
public class ConstantGuessRecommender extends MatrixRecommender {

    /**
     * given constant to predict the rating
     */
    private double constant;

    @Override
    public void trainModel() throws LibrecException {
        constant = (minRate + maxRate) / 2.0; // can also use given constant
    }

    /**
     * constant value as the predictive rating for user userIdx on item itemIdx.
     *
     * @param userIdx user index
     * @param itemIdx item index
     * @return predictive rating for user userIdx on item itemIdx
     */
    @Override
    protected double predict(int userIdx, int itemIdx) {
        return constant;
    }

}
